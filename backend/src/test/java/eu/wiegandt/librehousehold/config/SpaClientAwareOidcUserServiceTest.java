package eu.wiegandt.librehousehold.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class SpaClientAwareOidcUserServiceTest {

    @Mock
    private OAuth2UserService<OidcUserRequest, OidcUser> spaClientUserService;

    @Mock
    private OAuth2UserService<OidcUserRequest, OidcUser> socialLoginUserService;

    @Test
    void loadUser_spaClientRegistration_returnsUserOfSpaClientUserService() {
        // given
        var userRequest = userRequestFor(RegisteredClientSeeder.CLIENT_ID);
        var spaClientUser = oidcUser("member@example.com");
        doReturn(spaClientUser).when(spaClientUserService).loadUser(userRequest);
        var userService = new SpaClientAwareOidcUserService(spaClientUserService, socialLoginUserService);

        // when
        var result = userService.loadUser(userRequest);

        // then
        assertThat(result).isSameAs(spaClientUser);
    }

    @Test
    void loadUser_socialLoginRegistration_returnsUserOfSocialLoginUserService() {
        // given
        var userRequest = userRequestFor("dev-provider");
        var socialLoginUser = oidcUser("external-subject");
        doReturn(socialLoginUser).when(socialLoginUserService).loadUser(userRequest);
        var userService = new SpaClientAwareOidcUserService(spaClientUserService, socialLoginUserService);

        // when
        var result = userService.loadUser(userRequest);

        // then
        assertThat(result).isSameAs(socialLoginUser);
    }

    private OidcUserRequest userRequestFor(String registrationId) {
        var clientRegistration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/" + registrationId)
                .authorizationUri("http://localhost/authorize")
                .tokenUri("http://localhost/token")
                .build();
        var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token",
                Instant.now(), Instant.now().plusSeconds(300));
        return new OidcUserRequest(clientRegistration, accessToken, oidcUser("subject").getIdToken());
    }

    private OidcUser oidcUser(String subject) {
        var idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of(IdTokenClaimNames.SUB, subject, IdTokenClaimNames.ISS, "http://localhost/issuer"));
        return new DefaultOidcUser(List.of(), idToken);
    }
}
