package ${package};

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

public class ${authenticatorName}AuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "${providerId}";

    static final String CONFIG_GATE_ATTRIBUTE = "gateAttribute";

    private static final ${authenticatorName}Authenticator SINGLETON = new ${authenticatorName}Authenticator();

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "${displayName}";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Scaffolded authenticator (${providerId}) — replace this help text once the real "
                + "gating rule is in place.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(CONFIG_GATE_ATTRIBUTE)
                .label("Gate attribute")
                .helpText("User attribute checked by the placeholder rule. Set it to 'false' on a "
                        + "user to fail this authenticator. Defaults to '"
                        + ${authenticatorName}Authenticator.DEFAULT_GATE_ATTRIBUTE + "'.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(${authenticatorName}Authenticator.DEFAULT_GATE_ATTRIBUTE)
                .add()
                .build();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
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
