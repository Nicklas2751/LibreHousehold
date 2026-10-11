package eu.wiegandt.librehousehold.config;

import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * {@code oauth2Login()} accepts only one OIDC user service for all client registrations, but the
 * SPA's loop-back registration and the external social-login providers need different ones: the
 * loop-back user service resolves the member by the subject, which is the member's e-mail address
 * only for the embedded Authorization Server. External providers deliver an opaque subject instead;
 * their user is resolved to a member only afterwards, by {@code FederatedIdentityAuthenticationSuccessHandler}.
 */
class SpaClientAwareOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuth2UserService<OidcUserRequest, OidcUser> spaClientUserService;
    private final OAuth2UserService<OidcUserRequest, OidcUser> socialLoginUserService;

    SpaClientAwareOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> spaClientUserService,
                                  OAuth2UserService<OidcUserRequest, OidcUser> socialLoginUserService) {
        this.spaClientUserService = spaClientUserService;
        this.socialLoginUserService = socialLoginUserService;
    }

    @Override
    public OidcUser loadUser(@NonNull OidcUserRequest userRequest) {
        var registrationId = userRequest.getClientRegistration().getRegistrationId();
        var userService = RegisteredClientSeeder.CLIENT_ID.equals(registrationId) ? spaClientUserService : socialLoginUserService;
        return userService.loadUser(userRequest);
    }
}
