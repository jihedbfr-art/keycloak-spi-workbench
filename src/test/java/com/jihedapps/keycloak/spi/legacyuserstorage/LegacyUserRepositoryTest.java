package com.jihedapps.keycloak.spi.legacyuserstorage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real SQL against a real database — just H2 in-memory instead of Postgres, so this runs at
 * {@code mvn test} without Docker. {@link LegacyUserRepositoryPostgresIT} re-runs the same shape
 * of checks against actual PostgreSQL to catch anything H2's dialect papers over.
 */
class LegacyUserRepositoryTest {

    private Connection connection;
    private LegacyUserRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        String jdbcUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        connection = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = connection.createStatement()) {
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
                    ('2', 'mchen', 'mchen@example.com', 'Maria', 'Chen', 'hash2', 'salt2', TRUE),
                    ('3', 'disabled_user', 'disabled@example.com', 'No', 'Login', 'hash3', 'salt3', FALSE)
                    """);
        }

        repository = new LegacyUserRepository(() -> {
            try {
                return DriverManager.getConnection(jdbcUrl);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, LegacyTableConfig.defaults("legacy_subscriber"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void findsAUserByUsernameCaseInsensitively() {
        Optional<LegacyUser> found = repository.findByUsername("JSmith");

        assertThat(found).isPresent();
        assertThat(found.get().email()).isEqualTo("jsmith@example.com");
    }

    @Test
    void findsAUserByEmail() {
        Optional<LegacyUser> found = repository.findByEmail("mchen@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().username()).isEqualTo("mchen");
    }

    @Test
    void returnsEmptyForAnUnknownUsername() {
        assertThat(repository.findByUsername("nobody")).isEmpty();
    }

    @Test
    void disabledUsersAreStillReturnedByLookup() {
        // the provider decides what to do with a disabled account, not the repository
        Optional<LegacyUser> found = repository.findByUsername("disabled_user");

        assertThat(found).isPresent();
        assertThat(found.get().enabled()).isFalse();
    }

    @Test
    void searchMatchesAcrossUsernameEmailAndNameFields() {
        List<LegacyUser> byFirstName = repository.search("Maria", null, null);
        assertThat(byFirstName).extracting(LegacyUser::username).containsExactly("mchen");

        List<LegacyUser> byPartialEmail = repository.search("jsmith@", null, null);
        assertThat(byPartialEmail).extracting(LegacyUser::username).containsExactly("jsmith");
    }

    @Test
    void searchRespectsFirstResultAndMaxResultsPaging() {
        List<LegacyUser> all = repository.search("", null, null);
        assertThat(all).hasSize(3);

        List<LegacyUser> page = repository.search("", 1, 1);
        assertThat(page).hasSize(1);
        assertThat(page.get(0).username()).isEqualTo(all.get(1).username());
    }

    @Test
    void countsAllRows() {
        assertThat(repository.count()).isEqualTo(3);
    }
}
