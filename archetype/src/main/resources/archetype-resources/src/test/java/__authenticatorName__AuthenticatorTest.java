package ${package};

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.UserModel;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ${authenticatorName}AuthenticatorTest {

    private final ${authenticatorName}Authenticator authenticator = new ${authenticatorName}Authenticator();

    private AuthenticationFlowContext context;
    private UserModel user;
    private AuthenticatorConfigModel configModel;

    @BeforeEach
    void setUp() {
        context = mock(AuthenticationFlowContext.class);
        user = mock(UserModel.class);
        configModel = mock(AuthenticatorConfigModel.class);

        when(context.getUser()).thenReturn(user);
        when(context.getAuthenticatorConfig()).thenReturn(configModel);
        when(configModel.getConfig()).thenReturn(Map.of());
    }

    @Test
    void noUserResolvedYetPasses() {
        when(context.getUser()).thenReturn(null);

        authenticator.authenticate(context);

        verify(context).success();
        verify(context, never()).failure(any(AuthenticationFlowError.class));
    }

    @Test
    void defaultsToPassWhenGateAttributeIsNotSet() {
        when(user.getFirstAttribute("${providerId}_enabled")).thenReturn(null);

        authenticator.authenticate(context);

        verify(context).success();
    }

    @Test
    void gateAttributeSetToFalseFailsTheAuthenticator() {
        when(user.getFirstAttribute("${providerId}_enabled")).thenReturn("false");

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.ACCESS_DENIED);
        verify(context, never()).success();
    }

    @Test
    void customGateAttributeNameIsRespected() {
        when(configModel.getConfig()).thenReturn(Map.of("gateAttribute", "feature_flag"));
        when(user.getFirstAttribute("feature_flag")).thenReturn("false");

        authenticator.authenticate(context);

        verify(context).failure(AuthenticationFlowError.ACCESS_DENIED);
    }
}
