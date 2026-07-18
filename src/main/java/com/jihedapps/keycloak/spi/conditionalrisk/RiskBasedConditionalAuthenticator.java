package com.jihedapps.keycloak.spi.conditionalrisk;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.List;
import java.util.Set;

/**
 * Gates the rest of the authentication flow on a business rule instead of a fixed yes/no.
 * Meant to sit in front of an OTP execution so that step-up only fires when it actually matters,
 * rather than every login for every user regardless of context.
 *
 * <p>Evaluation order, first match wins:
 * <ol>
 *   <li>Explicit per-user override attribute ({@code otp_required} by default) — set to
 *       {@code true}/{@code false} to force the decision for a specific account, bypassing
 *       everything below. Useful for support cases ("this one account got compromised, force
 *       MFA on it") without touching the flow config.</li>
 *   <li>The client being authenticated against is in the configured risky-client list — an
 *       internal admin console should always require step-up, a public marketing site never
 *       should, independent of who the user is.</li>
 *   <li>The user's risk-tier attribute (default {@code risk_tier}) matches one of the
 *       configured risky values.</li>
 *   <li>Default: condition does not match, flow continues without the gated step.</li>
 * </ol>
 */
public class RiskBasedConditionalAuthenticator implements ConditionalAuthenticator {

    static final String DEFAULT_OVERRIDE_ATTRIBUTE = "otp_required";
    static final String DEFAULT_RISK_TIER_ATTRIBUTE = "risk_tier";

    public static final RiskBasedConditionalAuthenticator SINGLETON = new RiskBasedConditionalAuthenticator();

    @Override
    public boolean matchCondition(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            // no user resolved yet in the flow — nothing to gate on, don't force the step
            return false;
        }

        AuthenticatorConfigModel configModel = context.getAuthenticatorConfig();
        java.util.Map<String, String> config = configModel != null && configModel.getConfig() != null
                ? configModel.getConfig()
                : java.util.Map.of();

        Boolean override = readOverride(user, overrideAttribute(config));
        if (override != null) {
            return override;
        }

        if (context.getAuthenticationSession() != null
                && context.getAuthenticationSession().getClient() != null) {
            String clientId = context.getAuthenticationSession().getClient().getClientId();
            if (clientId != null && riskyClientIds(config).contains(clientId)) {
                return true;
            }
        }

        String riskTierAttribute = config.getOrDefault(
                RiskBasedConditionalAuthenticatorFactory.CONFIG_RISK_TIER_ATTRIBUTE,
                DEFAULT_RISK_TIER_ATTRIBUTE);
        String riskTier = user.getFirstAttribute(riskTierAttribute);
        return riskTier != null && riskyTiers(config).contains(riskTier.toLowerCase());
    }

    private Boolean readOverride(UserModel user, String attributeName) {
        String raw = user.getFirstAttribute(attributeName);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(raw)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return Boolean.FALSE;
        }
        // anything else (typo, leftover garbage) is treated as "no override", not as a crash
        return null;
    }

    private String overrideAttribute(java.util.Map<String, String> config) {
        return config.getOrDefault(
                RiskBasedConditionalAuthenticatorFactory.CONFIG_OVERRIDE_ATTRIBUTE,
                DEFAULT_OVERRIDE_ATTRIBUTE);
    }

    private Set<String> riskyClientIds(java.util.Map<String, String> config) {
        String raw = config.get(RiskBasedConditionalAuthenticatorFactory.CONFIG_RISKY_CLIENT_IDS);
        return splitCsv(raw);
    }

    private Set<String> riskyTiers(java.util.Map<String, String> config) {
        String raw = config.get(RiskBasedConditionalAuthenticatorFactory.CONFIG_RISKY_TIERS);
        Set<String> tiers = splitCsv(raw);
        if (tiers.isEmpty()) {
            return Set.of("high", "critical");
        }
        return tiers;
    }

    private Set<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(List.of(raw.toLowerCase().split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // conditional authenticators only gate — no user-facing action to run
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
        // stateless singleton, nothing to release
    }
}
