package com.jihedapps.keycloak.spi.conditionalrisk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskBasedConditionalAuthenticatorTest {

    private final RiskBasedConditionalAuthenticator authenticator = new RiskBasedConditionalAuthenticator();

    private AuthenticationFlowContext context;
    private UserModel user;
    private AuthenticationSessionModel authSession;
    private ClientModel client;
    private AuthenticatorConfigModel configModel;

    @BeforeEach
    void setUp() {
        context = mock(AuthenticationFlowContext.class);
        user = mock(UserModel.class);
        authSession = mock(AuthenticationSessionModel.class);
        client = mock(ClientModel.class);
        configModel = mock(AuthenticatorConfigModel.class);

        when(context.getUser()).thenReturn(user);
        when(context.getAuthenticationSession()).thenReturn(authSession);
        when(authSession.getClient()).thenReturn(client);
        when(context.getAuthenticatorConfig()).thenReturn(configModel);
        when(configModel.getConfig()).thenReturn(Map.of());
        when(client.getClientId()).thenReturn("some-client");
    }

    @Test
    void noUserResolvedYetDoesNotMatch() {
        when(context.getUser()).thenReturn(null);

        assertThat(authenticator.matchCondition(context)).isFalse();
    }

    @Test
    void defaultsToNoMatchWhenNothingIsConfigured() {
        when(user.getFirstAttribute("otp_required")).thenReturn(null);
        when(user.getFirstAttribute("risk_tier")).thenReturn(null);

        assertThat(authenticator.matchCondition(context)).isFalse();
    }

    @Nested
    class PerUserOverride {

        @Test
        void trueOverrideWinsEvenForALowRiskClient() {
            when(user.getFirstAttribute("otp_required")).thenReturn("true");

            assertThat(authenticator.matchCondition(context)).isTrue();
        }

        @Test
        void falseOverrideWinsEvenForARiskyClient() {
            when(configModel.getConfig()).thenReturn(Map.of("riskyClientIds", "some-client"));
            when(user.getFirstAttribute("otp_required")).thenReturn("false");

            assertThat(authenticator.matchCondition(context)).isFalse();
        }

        @Test
        void garbageOverrideValueIsTreatedAsNoOverride() {
            when(configModel.getConfig()).thenReturn(Map.of("riskyClientIds", "some-client"));
            when(user.getFirstAttribute("otp_required")).thenReturn("maybe");

            // falls through to the risky-client check below the override
            assertThat(authenticator.matchCondition(context)).isTrue();
        }

        @Test
        void customOverrideAttributeNameIsRespected() {
            when(configModel.getConfig()).thenReturn(Map.of("overrideAttribute", "force_mfa"));
            when(user.getFirstAttribute("force_mfa")).thenReturn("true");

            assertThat(authenticator.matchCondition(context)).isTrue();
        }
    }

    @Nested
    class RiskyClient {

        @Test
        void clientOnTheConfiguredListMatches() {
            when(configModel.getConfig()).thenReturn(Map.of("riskyClientIds", "admin-console, billing-backoffice"));
            when(client.getClientId()).thenReturn("billing-backoffice");

            assertThat(authenticator.matchCondition(context)).isTrue();
        }

        @Test
        void clientNotOnTheListDoesNotMatchOnItsOwn() {
            when(configModel.getConfig()).thenReturn(Map.of("riskyClientIds", "admin-console"));
            when(client.getClientId()).thenReturn("customer-portal");

            assertThat(authenticator.matchCondition(context)).isFalse();
        }
    }

    @Nested
    class RiskTier {

        @Test
        void defaultTiersHighAndCriticalMatch() {
            when(user.getFirstAttribute("risk_tier")).thenReturn("high");

            assertThat(authenticator.matchCondition(context)).isTrue();
        }

        @Test
        void lowTierDoesNotMatch() {
            when(user.getFirstAttribute("risk_tier")).thenReturn("low");

            assertThat(authenticator.matchCondition(context)).isFalse();
        }

        @Test
        void customTierAttributeAndValuesAreRespected() {
            when(configModel.getConfig()).thenReturn(Map.of(
                    "riskTierAttribute", "fraud_score_band",
                    "riskyTiers", "amber,red"));
            when(user.getFirstAttribute("fraud_score_band")).thenReturn("Amber");

            // comparison is case-insensitive
            assertThat(authenticator.matchCondition(context)).isTrue();
        }
    }
}
