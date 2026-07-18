package com.jihedapps.keycloak.spi.legacyuserstorage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The H2-based {@link LegacyUserRepositoryTest} proves the SQL is logically right and runs on
 * every {@code mvn test} without Docker. This proves the same queries survive contact with a real
 * database — case-insensitive LIKE and boolean handling aren't identical across every JDBC driver
 * and dialect, and this is the check that would actually catch it if they weren't.
 */
@Testcontainers
class LegacyUserRepositoryPostgresIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("legacydb")
                    .withUsername("legacy")
                    .withPassword("legacy");

    private LegacyUserRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS legacy_subscriber");
                stmt.execute("""
                        CREATE TABLE legacy_subscriber (
                            id VARCHAR(64) PRIMARY KEY,
                            username VARCHAR(64) NOT NULL,
                            email VARCHAR(128),
                            first_name VARCHAR(64),
                            last_name VARCHAR(64),
                            password_hash VARCHAR(128),
                            password_salt VARCHAR(64),
                            enabled BOOLEAN NOT NULL
                        )
                        """);
                stmt.execute("""
                        INSERT INTO legacy_subscriber VALUES
                        ('1', 'jsmith', 'jsmith@example.com', 'John', 'Smith', 'hash1', 'salt1', TRUE),
                        ('2', 'mchen', 'mchen@example.com', 'Maria', 'Chen', 'hash2', 'salt2', TRUE)
                        """);
            }
        }

        repository = new LegacyUserRepository(() -> {
            try {
                return DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, LegacyTableConfig.defaults("legacy_subscriber"));
    }

    @Test
    void findsAUserByUsernameCaseInsensitivelyOnRealPostgres() {
        Optional<LegacyUser> found = repository.findByUsername("JSMITH");

        assertThat(found).isPresent();
        assertThat(found.get().email()).isEqualTo("jsmith@example.com");
    }

    @Test
    void searchAndBooleanColumnMappingWorkOnRealPostgres() {
        List<LegacyUser> results = repository.search("Chen", null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).enabled()).isTrue();
    }
}
