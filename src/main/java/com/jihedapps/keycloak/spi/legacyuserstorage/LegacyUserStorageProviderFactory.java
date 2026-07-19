package com.jihedapps.keycloak.spi.legacyuserstorage;

import io.sentry.Sentry;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

public class LegacyUserStorageProviderFactory implements UserStorageProviderFactory<LegacyUserStorageProvider> {

    public static final String PROVIDER_ID = "legacy-user-storage";

    static final String CFG_JDBC_URL = "jdbcUrl";
    static final String CFG_JDBC_USERNAME = "jdbcUsername";
    static final String CFG_JDBC_PASSWORD = "jdbcPassword";
    static final String CFG_TABLE_NAME = "tableName";

    /**
     * Opt-in: no SENTRY_DSN env var means Sentry.init is never called, so LegacyUserRepository's
     * spans fall back to NoOpSpan and captureException calls are silent no-ops. Deployments that
     * don't want this provider talking to Sentry at all just don't set the variable.
     */
    @Override
    public void init(Config.Scope config) {
        String dsn = System.getenv("SENTRY_DSN");
        if (dsn != null && !dsn.isBlank() && !Sentry.isEnabled()) {
            Sentry.init(options -> {
                options.setDsn(dsn);
                options.setTracesSampleRate(1.0);
                options.setEnvironment(System.getenv().getOrDefault("SENTRY_ENVIRONMENT", "production"));
            });
        }
    }

    @Override
    public LegacyUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        String jdbcUrl = model.get(CFG_JDBC_URL);
        String jdbcUsername = model.get(CFG_JDBC_USERNAME);
        String jdbcPassword = model.get(CFG_JDBC_PASSWORD);
        String tableName = model.get(CFG_TABLE_NAME, "legacy_subscriber");

        var repository = new LegacyUserRepository(
                () -> {
                    try {
                        return DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword);
                    } catch (SQLException e) {
                        throw new LegacyUserStorageException("failed to connect to the legacy database", e);
                    }
                },
                LegacyTableConfig.defaults(tableName));

        return new LegacyUserStorageProvider(session, model, repository, new LegacyPasswordHasher.Sha256SaltedHasher());
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Federates users read-only from an existing (legacy) JDBC table — subscriber/CRM "
                + "style user data — instead of migrating everyone into Keycloak's own store up "
                + "front. New accounts should go straight into Keycloak; this is for the accounts "
                + "already sitting in the old system.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(CFG_JDBC_URL)
                .label("JDBC URL")
                .helpText("JDBC connection URL for the legacy database, e.g. jdbc:postgresql://host:5432/legacydb")
                .type(ProviderConfigProperty.STRING_TYPE)
                .add()
                .property()
                .name(CFG_JDBC_USERNAME)
                .label("JDBC username")
                .type(ProviderConfigProperty.STRING_TYPE)
                .add()
                .property()
                .name(CFG_JDBC_PASSWORD)
                .label("JDBC password")
                .type(ProviderConfigProperty.PASSWORD)
                .add()
                .property()
                .name(CFG_TABLE_NAME)
                .label("Table name")
                .helpText("Legacy table name. Expected columns (fixed for this provider): id, "
                        + "username, email, first_name, last_name, password_hash, password_salt, enabled.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("legacy_subscriber")
                .add()
                .build();
    }
}
