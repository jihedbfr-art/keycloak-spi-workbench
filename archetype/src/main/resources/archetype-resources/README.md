# ${artifactId}

Keycloak Authenticator SPI provider (`${providerId}`), scaffolded from
[keycloak-spi-authenticator-archetype](https://github.com/jihedbfr-art/keycloak-spi-workbench/tree/main/archetype).

## What's here

`${authenticatorName}Authenticator` / `Factory` — a placeholder gate that passes every login
through unless a configurable user attribute is explicitly set to `false`. Replace the body of
`shouldPass(...)` in `${authenticatorName}Authenticator` with the real rule; the rest (factory
metadata, config properties, `META-INF/services` registration) is the boilerplate every
Authenticator needs.

## Testing

```bash
mvn test    # unit test — no Docker required
mvn verify  # also boots a real Keycloak container and confirms the provider registers
```

## Install

```bash
mvn clean package
cp target/${artifactId}-${version}.jar $KEYCLOAK_HOME/providers/
$KEYCLOAK_HOME/bin/kc.sh build
```

Then add the "${displayName}" execution to an authentication flow.
