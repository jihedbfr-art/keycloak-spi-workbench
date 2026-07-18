package com.jihedapps.keycloak.spi.kafkaevents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaEventListenerProviderTest {

    private KafkaEventListenerProvider provider;

    @BeforeEach
    void setUp() {
        // producer/topic are irrelevant for these tests — only shouldForward()/toJson() are
        // exercised here, onEvent()'s actual Kafka send is covered by the Testcontainers IT
        provider = new KafkaEventListenerProvider(
                null, "auth-events", EnumSet.of(EventType.LOGIN, EventType.LOGOUT), new ObjectMapper());
    }

    @Test
    void forwardsConfiguredEventTypes() {
        assertThat(provider.shouldForward(EventType.LOGIN)).isTrue();
        assertThat(provider.shouldForward(EventType.LOGOUT)).isTrue();
    }

    @Test
    void doesNotForwardUnconfiguredEventTypes() {
        assertThat(provider.shouldForward(EventType.UPDATE_PASSWORD)).isFalse();
        assertThat(provider.shouldForward(EventType.REGISTER)).isFalse();
    }

    @Test
    void jsonPayloadCarriesTheFieldsAConsumerActuallyNeeds() throws Exception {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(EventType.LOGIN);
        when(event.getTime()).thenReturn(1_700_000_000_000L);
        when(event.getRealmId()).thenReturn("customer-realm");
        when(event.getClientId()).thenReturn("web-portal");
        when(event.getUserId()).thenReturn("user-123");
        when(event.getSessionId()).thenReturn("session-abc");
        when(event.getIpAddress()).thenReturn("10.0.0.5");
        when(event.getError()).thenReturn(null);

        String json = provider.toJson(event);

        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.readTree(json);
        assertThat(node.get("type").asText()).isEqualTo("LOGIN");
        assertThat(node.get("realmId").asText()).isEqualTo("customer-realm");
        assertThat(node.get("clientId").asText()).isEqualTo("web-portal");
        assertThat(node.get("userId").asText()).isEqualTo("user-123");
        assertThat(node.get("ipAddress").asText()).isEqualTo("10.0.0.5");
        assertThat(node.get("error").isNull()).isTrue();
    }

    @Test
    void loginErrorEventsCarryTheErrorReason() throws Exception {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(EventType.LOGIN_ERROR);
        when(event.getError()).thenReturn("invalid_user_credentials");

        String json = provider.toJson(event);

        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.readTree(json);
        assertThat(node.get("type").asText()).isEqualTo("LOGIN_ERROR");
        assertThat(node.get("error").asText()).isEqualTo("invalid_user_credentials");
    }
}
