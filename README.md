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

## Install

Build the provider jar and drop it into Keycloak's `providers/` directory:

```bash
git clone https://github.com/jihedbfr-art/keycloak-spi-workbench.git
cd keycloak-spi-workbench
mvn clean package
cp target/keycloak-spi-workbench-0.1.0.jar $KEYCLOAK_HOME/providers/
$KEYCLOAK_HOME/bin/kc.sh build
```

Then in an authentication flow: add execution "Condition - Risk-Based Step-Up", set it to
`REQUIRED`, configure the risky client IDs / risk-tier attribute / override attribute as needed,
and set the OTP execution right after it to `CONDITIONAL`.

## Testing

```bash
mvn test              # unit tests — the condition-matching logic, no Keycloak server involved
mvn verify             # also runs the Testcontainers deployment check against a real Keycloak
```

The unit tests (`RiskBasedConditionalAuthenticatorTest`) cover the four-step evaluation order
directly with mocked Keycloak model objects — override wins, then risky client, then risk tier,
then default. The `verify`-phase integration test spins up an actual `quay.io/keycloak/keycloak`
container, copies the built jar into `providers/`, boots the server, and hits the admin REST API
to confirm the provider is registered and shows up with the right display name — the thing that
actually breaks when an SPI has a packaging or `META-INF/services` mistake that unit tests can't
catch.

## Roadmap

- v0.2 — event listener SPI pushing login/logout events to Kafka
- v0.3 — user storage SPI federating against a legacy user table
- v1.0 — Maven archetype for scaffolding a new tested SPI module

## License

MIT — see [LICENSE](LICENSE).
