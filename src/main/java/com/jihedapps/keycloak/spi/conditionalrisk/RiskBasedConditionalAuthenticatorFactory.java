package com.jihedapps.keycloak.spi.conditionalrisk;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

public class RiskBasedConditionalAuthenticatorFactory implements ConditionalAuthenticatorFactory {

    public static final String PROVIDER_ID = "conditional-risk-based";

    static final String CONFIG_RISKY_CLIENT_IDS = "riskyClientIds";
    static final String CONFIG_RISK_TIER_ATTRIBUTE = "riskTierAttribute";
    static final String CONFIG_RISKY_TIERS = "riskyTiers";
    static final String CONFIG_OVERRIDE_ATTRIBUTE = "overrideAttribute";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Condition - Risk-Based Step-Up";
    }

    @Override
    public String getReferenceCategory() {
        return "condition";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Matches when the login should be treated as higher risk: the user has an "
                + "explicit override attribute, the client is on the configured risky-client "
                + "list, or the user's risk-tier attribute is in the configured risky tiers. "
                + "Wire the subsequent execution in this flow (e.g. OTP) as CONDITIONAL so it "
                + "only fires when this condition matches.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(CONFIG_RISKY_CLIENT_IDS)
                .label("Risky client IDs")
                .helpText("Comma-separated Keycloak client IDs that always match this condition, "
                        + "regardless of the user (e.g. an internal admin console).")
                .type(ProviderConfigProperty.STRING_TYPE)
                .add()
                .property()
                .name(CONFIG_RISK_TIER_ATTRIBUTE)
                .label("Risk tier user attribute")
                .helpText("Name of the user attribute holding a risk tier. Defaults to 'risk_tier'.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(RiskBasedConditionalAuthenticator.DEFAULT_RISK_TIER_ATTRIBUTE)
                .add()
                .property()
                .name(CONFIG_RISKY_TIERS)
                .label("Risky tier values")
                .helpText("Comma-separated values of the risk tier attribute considered risky. "
                        + "Defaults to 'high,critical'.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("high,critical")
                .add()
                .property()
                .name(CONFIG_OVERRIDE_ATTRIBUTE)
                .label("Per-user override attribute")
                .helpText("Name of a user attribute ('true'/'false') that forces the decision for "
                        + "one account, bypassing client and tier checks. Defaults to 'otp_required'.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(RiskBasedConditionalAuthenticator.DEFAULT_OVERRIDE_ATTRIBUTE)
                .add()
                .build();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return RiskBasedConditionalAuthenticator.SINGLETON;
    }

    @Override
    public ConditionalAuthenticator getSingleton() {
        return RiskBasedConditionalAuthenticator.SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        // no global config needed — everything is per-execution via getConfigProperties()
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // nothing to wire up after all factories are loaded
    }

    @Override
    public void close() {
        // stateless factory, nothing to release
    }
}
