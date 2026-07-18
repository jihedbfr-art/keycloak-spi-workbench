# Changelog

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
