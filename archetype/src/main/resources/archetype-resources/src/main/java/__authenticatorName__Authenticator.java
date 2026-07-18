package ${package};

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Map;

/**
 * Scaffolded by keycloak-spi-authenticator-archetype. Placeholder logic: passes the user through
 * unless a gate attribute is explicitly set to "false", so the provider is deployable and
 * testable out of the box. Replace {@link #shouldPass(AuthenticationFlowContext)} with the real
 * rule — everything else (success/fail wiring, requiresUser, close) is boilerplate every
 * Authenticator needs and can usually stay as-is.
 */
public class ${authenticatorName}Authenticator implements Authenticator {

    static final String DEFAULT_GATE_ATTRIBUTE = "${providerId}_enabled";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (shouldPass(context)) {
            context.success();
        } else {
            context.failure(AuthenticationFlowError.ACCESS_DENIED);
        }
    }

    // TODO: replace with the real condition this authenticator should enforce.
    private boolean shouldPass(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            return true;
        }

        String gateAttribute = gateAttribute(context.getAuthenticatorConfig());
        String raw = user.getFirstAttribute(gateAttribute);
        return !"false".equalsIgnoreCase(raw);
    }

    private String gateAttribute(AuthenticatorConfigModel configModel) {
        Map<String, String> config = configModel != null && configModel.getConfig() != null
                ? configModel.getConfig()
                : Map.of();
        return config.getOrDefault(${authenticatorName}AuthenticatorFactory.CONFIG_GATE_ATTRIBUTE, DEFAULT_GATE_ATTRIBUTE);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // no user-facing form for this authenticator — authenticate() alone decides
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // nothing to set up on the user account for this authenticator
    }

    @Override
    public void close() {
        // stateless, nothing to release
    }
}
