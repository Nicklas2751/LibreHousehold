package eu.wiegandt.librehousehold.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SocialLoginRegistrationCheckTest {

    private final SocialLoginRegistrationCheck check = new SocialLoginRegistrationCheck();

    @Nested
    class check {

        @Test
        void issuerWithoutOpenid_throwsIllegalStateException() {
            // given
            var registration = oidcRegistration().scope("profile", "email").build();

            // when / then
            assertThatIllegalStateException().isThrownBy(() -> check.check(List.of(registration)));
        }

        @Test
        void openidWithoutIssuer_throwsIllegalStateException() {
            // given
            var registration = oidcRegistration().issuerUri(null).build();

            // when / then
            assertThatIllegalStateException().isThrownBy(() -> check.check(List.of(registration)));
        }

        @Test
        void registrationWithoutEmailScope_throwsIllegalStateException() {
            // given
            var registration = oidcRegistration().scope("openid", "profile").build();

            // when / then
            assertThatIllegalStateException().isThrownBy(() -> check.check(List.of(registration)));
        }

        @Test
        void githubWithoutUserEmailScope_throwsIllegalStateException() {
            // given
            var registration = CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId("client-id").clientSecret("client-secret").build();

            // when / then
            assertThatIllegalStateException().isThrownBy(() -> check.check(List.of(registration)));
        }

        @Test
        void googleCommonProviderDefaults_doesNotThrow() {
            // given
            var registration = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId("client-id").clientSecret("client-secret").build();

            // when / then
            assertThatCode(() -> check.check(List.of(registration))).doesNotThrowAnyException();
        }

        @Test
        void completeGoogleAndGithub_doesNotThrow() {
            // given
            var google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId("client-id").clientSecret("client-secret").build();
            var github = CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId("client-id").clientSecret("client-secret").scope("read:user", "user:email").build();

            // when / then
            assertThatCode(() -> check.check(List.of(google, github))).doesNotThrowAnyException();
        }
    }

    private ClientRegistration.Builder oidcRegistration() {
        return ClientRegistration.withRegistrationId("keycloak")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri("https://keycloak.example.com/realms/household")
                .authorizationUri("https://keycloak.example.com/realms/household/protocol/openid-connect/auth")
                .tokenUri("https://keycloak.example.com/realms/household/protocol/openid-connect/token")
                .jwkSetUri("https://keycloak.example.com/realms/household/protocol/openid-connect/certs")
                .userInfoUri("https://keycloak.example.com/realms/household/protocol/openid-connect/userinfo")
                .userNameAttributeName("sub");
    }
}
