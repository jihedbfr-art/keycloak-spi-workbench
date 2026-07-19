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

## What's here: a legacy user storage provider

`legacy-user-storage` federates users read-only from an existing JDBC table instead of forcing a
big-bang migration into Keycloak's own store. This is the shape a real migration usually needs:
the legacy system (a BSS subscriber table, an old CRM, whatever) stays the source of truth for
accounts that already exist, new accounts go straight into Keycloak, and nobody has to write
sync logic in both directions. Supports lookup by username/email/id, admin-console search, and
password validation against the legacy hash — read-only, no writes ever go back to the legacy
table. Table and column names are configurable; the hash algorithm is pluggable via
`LegacyPasswordHasher` (the default, SHA-256 of salt+password, is a placeholder — swap it for
whatever your actual legacy system used before pointing this at real data).

Plain JDBC, no ORM, no connection pool — the connection supplier is the seam where a real
deployment would plug in a pooled `DataSource` instead. Optional Sentry error and performance
monitoring: set `SENTRY_DSN` and every JDBC call gets a span, `SQLException`s get reported. No
DSN, no Sentry — nothing changes.

## Install

Build the provider jar and drop it into Keycloak's `providers/` directory:

```bash
git clone https://github.com/jihedbfr-art/keycloak-spi-workbench.git
cd keycloak-spi-workbench
mvn clean package
cp target/keycloak-spi-workbench-0.4.0.jar $KEYCLOAK_HOME/providers/
```

You'll also need the JDBC driver for your legacy database (e.g. `postgresql-42.7.3.jar`) sitting
in `providers/` alongside it, then:

```bash
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

For the legacy user storage provider: Realm settings → User federation → Add provider →
`legacy-user-storage`, then set the JDBC URL, username, password, and table name. The table is
expected to have columns `id`, `username`, `email`, `first_name`, `last_name`, `password_hash`,
`password_salt`, `enabled` (fixed for now — configurable column names are a natural v0.4).

## Testing

```bash
mvn test              # unit tests — no Docker required
mvn verify             # also runs the Testcontainers checks: real Keycloak, real Kafka, real Postgres
```

`RiskBasedConditionalAuthenticatorTest` covers the four-step evaluation order directly with
mocked Keycloak model objects. `KafkaEventListenerProviderTest` / `...FactoryTest` cover event
filtering, JSON payload shape, and config parsing. `LegacyUserRepositoryTest` runs real SQL
against an in-memory H2 database (fast, no Docker) and `LegacyUserStorageProviderTest` covers the
credential-validation and search-delegation logic with a faked repository. Three integration
tests run at the `verify` phase: one boots a real `quay.io/keycloak/keycloak` container with the
built jar deployed and confirms the conditional authenticator registers via the admin REST API;
one spins up a real Kafka broker and proves a message round-trips through a real producer and
consumer; one re-runs the legacy-storage queries against a real PostgreSQL container, since H2's
dialect doesn't always match Postgres exactly (case-insensitive `LIKE` and boolean columns being
the two that actually differ in practice).

## Scaffolding a new provider

The [`archetype/`](archetype) directory has a Maven archetype that generates a new `Authenticator`
SPI module in this same shape — placeholder authenticator + factory, `META-INF/services`
registration, a unit test, and a Testcontainers deployment IT — instead of copying one of the
three providers above by hand and renaming things. See [archetype/README.md](archetype/README.md)
for the generate command. It's a standalone build, not part of this project's own `mvn` reactor.

## Roadmap

- Archetype coverage for the event-listener and user-storage provider shapes, if the authenticator
  one earns its keep
- Configurable column names for `legacy-user-storage` (currently fixed, see Install above)

## License

MIT — see [LICENSE](LICENSE).
