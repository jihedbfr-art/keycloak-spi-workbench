# Changelog

## 0.1.0 — 2026-07-17

First provider: `conditional-risk-based`.

- `RiskBasedConditionalAuthenticator` / `Factory`: a Keycloak `ConditionalAuthenticator` that
  gates a flow step on a per-user override attribute, a configured risky-client list, or a
  user's risk-tier attribute, in that order.
- Unit tests covering the full evaluation order with mocked Keycloak model objects.
- Testcontainers-based deployment check (bound to `mvn verify`) that boots a real Keycloak
  server with the built jar and confirms the provider registers correctly.
