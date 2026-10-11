package eu.wiegandt.librehousehold.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

/**
 * {@code oauth2Login()} accepts only one success handler for all client registrations: the SPA's
 * loop-back login has finished at this point, while a social login still has to be mapped to a
 * member account first (see {@link FederatedIdentityAuthenticationSuccessHandler}).
 */
class SpaClientAwareAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthenticationSuccessHandler spaClientSuccessHandler;
    private final AuthenticationSuccessHandler socialLoginSuccessHandler;

    SpaClientAwareAuthenticationSuccessHandler(AuthenticationSuccessHandler spaClientSuccessHandler,
                                               AuthenticationSuccessHandler socialLoginSuccessHandler) {
        this.spaClientSuccessHandler = spaClientSuccessHandler;
        this.socialLoginSuccessHandler = socialLoginSuccessHandler;
    }

    @Override
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                        @NonNull Authentication authentication) throws IOException, ServletException {
        // oauth2Login() always authenticates with an OAuth2AuthenticationToken
        var registrationId = ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();
        var successHandler = RegisteredClientSeeder.CLIENT_ID.equals(registrationId)
                ? spaClientSuccessHandler
                : socialLoginSuccessHandler;
        successHandler.onAuthenticationSuccess(request, response, authentication);
    }
}
