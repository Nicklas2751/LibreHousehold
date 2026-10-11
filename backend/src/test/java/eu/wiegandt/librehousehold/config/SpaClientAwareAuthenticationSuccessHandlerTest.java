package eu.wiegandt.librehousehold.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SpaClientAwareAuthenticationSuccessHandlerTest {

    @Mock
    private AuthenticationSuccessHandler spaClientSuccessHandler;

    @Mock
    private AuthenticationSuccessHandler socialLoginSuccessHandler;

    @Test
    void onAuthenticationSuccess_spaClientRegistration_delegatesToSpaClientSuccessHandlerOnly() throws Exception {
        // given
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var authentication = authenticationFor(RegisteredClientSeeder.CLIENT_ID);
        var successHandler = new SpaClientAwareAuthenticationSuccessHandler(spaClientSuccessHandler, socialLoginSuccessHandler);

        // when
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // then
        verify(spaClientSuccessHandler).onAuthenticationSuccess(request, response, authentication);
        verifyNoInteractions(socialLoginSuccessHandler);
    }

    @Test
    void onAuthenticationSuccess_socialLoginRegistration_delegatesToSocialLoginSuccessHandlerOnly() throws Exception {
        // given
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var authentication = authenticationFor("dev-provider");
        var successHandler = new SpaClientAwareAuthenticationSuccessHandler(spaClientSuccessHandler, socialLoginSuccessHandler);

        // when
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // then
        verify(socialLoginSuccessHandler).onAuthenticationSuccess(request, response, authentication);
        verifyNoInteractions(spaClientSuccessHandler);
    }

    private OAuth2AuthenticationToken authenticationFor(String registrationId) {
        var user = new DefaultOAuth2User(List.of(), Map.of("sub", "subject"), "sub");
        return new OAuth2AuthenticationToken(user, List.of(), registrationId);
    }
}
