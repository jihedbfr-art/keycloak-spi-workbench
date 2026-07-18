package com.jihedapps.keycloak.spi.legacyuserstorage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyTableConfigTest {

    @Test
    void acceptsPlainIdentifiers() {
        LegacyTableConfig config = LegacyTableConfig.defaults("legacy_subscriber");

        assertThat(config.tableName()).isEqualTo("legacy_subscriber");
        assertThat(config.usernameColumn()).isEqualTo("username");
    }

    @Test
    void rejectsATableNameThatIsntAPlainIdentifier() {
        assertThatThrownBy(() -> LegacyTableConfig.defaults("legacy_subscriber; DROP TABLE users;--"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");
    }

    @Test
    void rejectsAnIdentifierStartingWithADigit() {
        assertThatThrownBy(() -> LegacyTableConfig.defaults("1_legacy"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANullTableName() {
        assertThatThrownBy(() -> LegacyTableConfig.defaults(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
