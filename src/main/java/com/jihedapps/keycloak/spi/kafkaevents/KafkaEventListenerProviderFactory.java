package com.jihedapps.keycloak.spi.kafkaevents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

/**
 * One {@link KafkaProducer} shared across every request Keycloak handles — producers are
 * thread-safe and expensive to create, so building one per {@link #create} call would defeat
 * the point of using Kafka's own internal batching.
 */
public class KafkaEventListenerProviderFactory implements EventListenerProviderFactory {

    public static final String PROVIDER_ID = "kafka-event-publisher";

    static final Set<EventType> DEFAULT_FORWARDED_TYPES =
            EnumSet.of(EventType.LOGIN, EventType.LOGIN_ERROR, EventType.LOGOUT);

    private Producer<String, String> producer;
    private String topic;
    private Set<EventType> forwardedTypes;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new KafkaEventListenerProvider(producer, topic, forwardedTypes, mapper);
    }

    @Override
    public void init(Config.Scope config) {
        String bootstrapServers = config.get("bootstrapServers", "localhost:9092");
        this.topic = config.get("topic", "keycloak-auth-events");
        this.forwardedTypes = parseEventTypes(config.get("eventTypes"));

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // a login flow should never block waiting on Kafka — fail fast and let onEvent's
        // callback log the drop rather than stalling authentication
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000);
        props.put(ProducerConfig.ACKS_CONFIG, "1");

        this.producer = new KafkaProducer<>(props);
    }

    static Set<EventType> parseEventTypes(String csv) {
        if (csv == null || csv.isBlank()) {
            return DEFAULT_FORWARDED_TYPES;
        }
        Set<EventType> types = EnumSet.noneOf(EventType.class);
        for (String raw : csv.split(",")) {
            String name = raw.trim().toUpperCase();
            if (name.isEmpty()) {
                continue;
            }
            try {
                types.add(EventType.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // an unknown event type name in config shouldn't crash server startup — skip it
            }
        }
        return types.isEmpty() ? DEFAULT_FORWARDED_TYPES : types;
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // nothing to wire up after all factories are loaded
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void close() {
        if (producer != null) {
            producer.close();
        }
    }
}
