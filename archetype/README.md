# keycloak-spi-authenticator-archetype

Maven archetype that scaffolds a new Keycloak `Authenticator` SPI module in the same shape as the
providers in this repo: a placeholder authenticator + factory pair, `META-INF/services`
registration, a unit test against mocked Keycloak model objects, and a Testcontainers deployment
IT that boots a real Keycloak server with the built jar and checks the provider registers.

Standalone build — not part of the parent jar's reactor, and not shaded (a plain authenticator
has no third-party runtime dependency to bundle; add the shade plugin back if yours does, the
same way `kafka-event-publisher` needed it).

## Generate a new provider

```bash
cd archetype
mvn install

cd /path/to/somewhere-else
mvn archetype:generate \
  -DarchetypeGroupId=com.jihedapps.keycloak \
  -DarchetypeArtifactId=keycloak-spi-authenticator-archetype \
  -DarchetypeVersion=1.0.0 \
  -DgroupId=com.example \
  -DartifactId=geo-block-authenticator \
  -Dversion=0.1.0-SNAPSHOT \
  -Dpackage=com.example.keycloak.spi.geoblock \
  -DauthenticatorName=GeoBlock \
  -DproviderId=geo-block \
  -DdisplayName="Geo Block Authenticator" \
  -DinteractiveMode=false
```

That produces a ready-to-build module: `GeoBlockAuthenticator` / `GeoBlockAuthenticatorFactory`,
registered under the `geo-block` provider ID, with tests already wired up. Replace the placeholder
rule in `shouldPass(...)` and go.

## Self-test

```bash
mvn install
```

runs the archetype's own integration test (`src/test/resources/projects/basic`): generates a
sample project from the archetype and runs `mvn package` against it, so a broken template fails
here instead of silently shipping.

## Only covers Authenticator today

This archetype targets the `Authenticator`/`AuthenticatorFactory` SPI — the most common one and
the easiest generic starting point. `kafka-event-publisher` (event listener) and
`legacy-user-storage` (user federation) have different lifecycles and config shapes; scaffolding
those is a natural follow-up if this one earns its keep.
