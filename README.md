# keycloak-spi-workbench

[![CI](https://github.com/jihedbfr-art/keycloak-spi-workbench/actions/workflows/ci.yml/badge.svg)](https://github.com/jihedbfr-art/keycloak-spi-workbench/actions)

Custom Keycloak SPI providers, built one at a time, each with real tests. Most public Keycloak
SPI examples stop at "here's a class that implements the interface" — the actual hard part is
proving the thing deploys cleanly on a real server and behaves correctly once it's there, and
that part is usually missing.

## What's here: a risk-based conditional authenticator

`conditional-risk-based` gates a step in the login flow (typically OTP) on a business rule
instead of a flat yes/no for every user, every time:

1. a per-user override attribute forces the decision for one account (support case: "force MFA
   on this one"), bypassing everything else
2. the client being authenticated against is on a configured risky-client list — an internal
   admin console should always demand step-up, a public marketing site never should
3. the user's risk-tier attribute matches a configured risky value
4. otherwise the condition doesn't match and the flow continues without the extra step

This is the kind of rule real BSS/telecom-style access control actually needs — "this account
touches billing overrides, always challenge it" or "this client is the ops console, always
step up" — instead of the binary MFA-for-everyone-or-no-one that most tutorials show.

## What's here: a Kafka event listener

`kafka-event-publisher` forwards `LOGIN`, `LOGIN_ERROR`, and `LOGOUT` events to a Kafka topic —
for the downstream systems that care whether a login succeeded, not for Keycloak's own event
log (it already has one). Admin events are deliberately not forwarded; that's a different
consumer's problem and mixing the two shapes into one topic just pushes filtering work onto
everyone reading it. Configurable via the provider config: `bootstrapServers`, `topic`,
`eventTypes` (comma-separated, defaults to login/login-error/logout). A Kafka outage never blocks
a login — the producer fails fast (`max.block.ms=2000`) and a failed send is logged, not thrown.

The jar is shaded — `kafka-clients` and `jackson-databind` are bundled in, since Keycloak's
`providers/` directory doesn't resolve dependencies for you.

## Install

Build the provider jar and drop it into Keycloak's `providers/` directory:

```bash
git clone https://github.com/jihedbfr-art/keycloak-spi-workbench.git
cd keycloak-spi-workbench
mvn clean package
cp target/keycloak-spi-workbench-0.2.0.jar $KEYCLOAK_HOME/providers/
$KEYCLOAK_HOME/bin/kc.sh build
```

For the conditional authenticator: in an authentication flow, add execution "Condition -
Risk-Based Step-Up", set it to `REQUIRED`, configure the risky client IDs / risk-tier attribute /
override attribute as needed, and set the OTP execution right after it to `CONDITIONAL`.

For the Kafka publisher: add `kafka-event-publisher` to the realm's event listeners
(Realm settings → Events → Event listeners), then configure it at start-up, e.g.:

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-events-listener-kafka-event-publisher-bootstrap-servers=kafka:9092 \
  --spi-events-listener-kafka-event-publisher-topic=auth-events
```

## Testing

```bash
mvn test              # unit tests — no Keycloak server or Kafka broker involved
mvn verify             # also runs the Testcontainers checks against real Keycloak and real Kafka
```

`RiskBasedConditionalAuthenticatorTest` covers the four-step evaluation order directly with
mocked Keycloak model objects. `KafkaEventListenerProviderTest` / `...FactoryTest` cover event
filtering, JSON payload shape, and config parsing. Two integration tests run at the `verify`
phase: one boots a real `quay.io/keycloak/keycloak` container with the built jar deployed and
confirms the conditional authenticator registers via the admin REST API; the other spins up a
real Kafka broker, calls the event listener with a real `KafkaProducer`, and reads the message
back with a real consumer — proving the producer config (serializers, acks) is actually right,
not just that the mocked call happened.

## Roadmap

- v0.3 — user storage SPI federating against a legacy user table
- v1.0 — Maven archetype for scaffolding a new tested SPI module

## License

MIT — see [LICENSE](LICENSE).
