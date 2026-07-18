package com.jihedapps.keycloak.spi.kafkaevents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Publishes a small, stable set of auth events to Kafka. Deliberately narrow scope: this is not
 * a general-purpose event sink (Keycloak already has one — the JBoss logging event listener),
 * it's specifically for systems downstream that care about "did this login succeed or fail",
 * e.g. a fraud-detection pipeline or an audit trail that outlives Keycloak's own event store.
 *
 * <p>Admin events (realm/client config changes) are intentionally not forwarded — different
 * consumers care about those, and mixing them into the same topic would force every consumer to
 * filter on event shape instead of just reading what they subscribed to.
 */
public class KafkaEventListenerProvider implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(KafkaEventListenerProvider.class);

    private final Producer<String, String> producer;
    private final String topic;
    private final Set<EventType> forwardedTypes;
    private final ObjectMapper mapper;

    public KafkaEventListenerProvider(Producer<String, String> producer, String topic,
                                       Set<EventType> forwardedTypes, ObjectMapper mapper) {
        this.producer = producer;
        this.topic = topic;
        this.forwardedTypes = forwardedTypes;
        this.mapper = mapper;
    }

    @Override
    public void onEvent(Event event) {
        if (!shouldForward(event.getType())) {
            return;
        }
        String key = event.getUserId() != null ? event.getUserId() : event.getSessionId();
        String payload = toJson(event);
        producer.send(new ProducerRecord<>(topic, key, payload), (metadata, exception) -> {
            if (exception != null) {
                // never let a Kafka outage take the login flow down with it — log and move on
                LOG.warnf(exception, "failed to publish %s event for realm %s to Kafka",
                        event.getType(), event.getRealmId());
            }
        });
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        // out of scope for this provider, see class javadoc
    }

    boolean shouldForward(EventType type) {
        return forwardedTypes.contains(type);
    }

    String toJson(Event event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("type", event.getType() != null ? event.getType().name() : null);
        fields.put("time", event.getTime());
        fields.put("realmId", event.getRealmId());
        fields.put("clientId", event.getClientId());
        fields.put("userId", event.getUserId());
        fields.put("sessionId", event.getSessionId());
        fields.put("ipAddress", event.getIpAddress());
        fields.put("error", event.getError());
        try {
            return mapper.writeValueAsString(fields);
        } catch (Exception e) {
            // a serialization failure here is a bug in this class, not something to crash a login over
            LOG.warnf(e, "failed to serialize %s event", event.getType());
            return "{}";
        }
    }

    @Override
    public void close() {
        // the producer is shared across provider instances and closed by the factory, not here
    }
}
