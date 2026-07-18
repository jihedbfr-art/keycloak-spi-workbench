package com.jihedapps.keycloak.spi.kafkaevents;

import org.junit.jupiter.api.Test;
import org.keycloak.events.EventType;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaEventListenerProviderFactoryTest {

    @Test
    void blankConfigFallsBackToDefaults() {
        assertThat(KafkaEventListenerProviderFactory.parseEventTypes(null))
                .isEqualTo(KafkaEventListenerProviderFactory.DEFAULT_FORWARDED_TYPES);
        assertThat(KafkaEventListenerProviderFactory.parseEventTypes("  "))
                .isEqualTo(KafkaEventListenerProviderFactory.DEFAULT_FORWARDED_TYPES);
    }

    @Test
    void parsesACommaSeparatedList() {
        assertThat(KafkaEventListenerProviderFactory.parseEventTypes("login, logout, register"))
                .containsExactlyInAnyOrder(EventType.LOGIN, EventType.LOGOUT, EventType.REGISTER);
    }

    @Test
    void unknownEventTypeNamesAreSkippedNotFatal() {
        assertThat(KafkaEventListenerProviderFactory.parseEventTypes("login, not_a_real_type, logout"))
                .containsExactlyInAnyOrder(EventType.LOGIN, EventType.LOGOUT);
    }

    @Test
    void allUnknownNamesFallsBackToDefaults() {
        assertThat(KafkaEventListenerProviderFactory.parseEventTypes("nope, still_not_real"))
                .isEqualTo(KafkaEventListenerProviderFactory.DEFAULT_FORWARDED_TYPES);
    }
}
