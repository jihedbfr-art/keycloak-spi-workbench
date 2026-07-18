# Changelog

## 0.3.0 — 2026-07-18

Third provider: `legacy-user-storage`.

- `LegacyUserStorageProvider` / `Factory`: read-only `UserStorageProvider` federating an existing
  JDBC table — lookup by username/email/id, admin-console search, password validation against a
  pluggable legacy hash (`LegacyPasswordHasher`, default SHA-256 salted). No writes ever go back
  to the legacy table.
- Plain JDBC (`LegacyUserRepository`), no ORM or pool — a connection-supplier seam for plugging in
  a pooled `DataSource` in a real deployment.
- Unit tests: `LegacyUserRepositoryTest` runs real SQL against in-memory H2 (no Docker),
  `LegacyUserStorageProviderTest` covers credential validation and search delegation against a
  faked repository, plus hasher and table-identifier-validation tests.
- Testcontainers integration test against real PostgreSQL, re-running the repository queries to
  catch dialect differences H2 doesn't surface.

## 0.2.0 — 2026-07-18

Second provider: `kafka-event-publisher`.

- `KafkaEventListenerProvider` / `Factory`: forwards `LOGIN`, `LOGIN_ERROR`, `LOGOUT` (configurable)
  events to a Kafka topic. Admin events are not forwarded. Producer fails fast and logs on error
  instead of blocking or breaking the login flow.
- Jar is now shaded (`kafka-clients` + `jackson-databind` bundled) since Keycloak doesn't resolve
  provider dependencies on its own.
- Unit tests for event filtering, JSON payload shape, and config parsing.
- Testcontainers integration test against a real Kafka broker: publishes through the real
  producer config and reads the message back with a real consumer.

## 0.1.0 — 2026-07-17

First provider: `conditional-risk-based`.

- `RiskBasedConditionalAuthenticator` / `Factory`: a Keycloak `ConditionalAuthenticator` that
  gates a flow step on a per-user override attribute, a configured risky-client list, or a
  user's risk-tier attribute, in that order.
- Unit tests covering the full evaluation order with mocked Keycloak model objects.
- Testcontainers-based deployment check (bound to `mvn verify`) that boots a real Keycloak
  server with the built jar and confirms the provider registers correctly.
