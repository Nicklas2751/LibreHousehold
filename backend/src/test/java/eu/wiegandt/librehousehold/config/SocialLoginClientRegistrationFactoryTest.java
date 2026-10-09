package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderConfig;
import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SocialLoginClientRegistrationFactoryTest {

    private static final String ISSUER_URI = "https://keycloak.example.com/realms/household";

    @Mock
    private OidcIssuerDiscovery oidcIssuerDiscovery;

    @Spy
    private CommonProvider commonProvider = new CommonProvider();

    @InjectMocks
    private SocialLoginClientRegistrationFactory factory;

    @Nested
    class buildClientRegistrations {

        @Test
        void oidcProviderWithIssuerUri_registrationFromDiscoveryWithConfiguredClient() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("keycloak-self-hosted",
                    new ProviderConfig(ProviderType.OIDC, ISSUER_URI, "client-id", "client-secret", "Keycloak",
                            null, null, null, null)));
            doReturn(discoveredRegistration()).when(oidcIssuerDiscovery).discover(ISSUER_URI);
            var expected = discoveredRegistration()
                    .registrationId("keycloak-self-hosted")
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .clientName("Keycloak")
                    .build();

            // when
            var result = factory.buildClientRegistrations(properties);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
        }

        @Test
        void oidcProviderWithIssuerUriAndCommonProviderName_registrationFromDiscovery() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("google",
                    new ProviderConfig(ProviderType.OIDC, ISSUER_URI, "client-id", "client-secret", "Google",
                            null, null, null, null)));
            doReturn(discoveredRegistration()).when(oidcIssuerDiscovery).discover(ISSUER_URI);
            var expected = discoveredRegistration()
                    .registrationId("google")
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .clientName("Google")
                    .build();

            // when
            var result = factory.buildClientRegistrations(properties);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
        }

        @Test
        void oidcProviderWithoutIssuerUriAndCommonProviderName_registrationFromCommonOAuth2Provider() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("google",
                    new ProviderConfig(ProviderType.OIDC, null, "client-id", "client-secret", "Google",
                            null, null, null, null)));
            var expected = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .clientName("Google")
                    .build();

            // when
            var result = factory.buildClientRegistrations(properties);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
        }

        @Test
        void oidcProviderWithBlankIssuerUriAndCommonProviderName_registrationFromCommonOAuth2Provider() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("google",
                    new ProviderConfig(ProviderType.OIDC, "", "client-id", "client-secret", "Google",
                            null, null, null, null)));
            var expected = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .clientName("Google")
                    .build();

            // when
            var result = factory.buildClientRegistrations(properties);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
        }

        @Test
        void oidcProviderWithBlankIssuerUriAndCommonProviderName_issuerDiscoveryNotCalled() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("google",
                    new ProviderConfig(ProviderType.OIDC, " ", "client-id", "client-secret", "Google",
                            null, null, null, null)));

            // when
            factory.buildClientRegistrations(properties);

            // then
            verifyNoInteractions(oidcIssuerDiscovery);
        }

        @Test
        void providerWithoutTypeAndCommonProviderName_registrationFromCommonOAuth2Provider() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("google",
                    new ProviderConfig(null, null, "client-id", "client-secret", "Google",
                            null, null, null, null)));
            var expected = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .clientName("Google")
                    .build();

            // when
            var result = factory.buildClientRegistrations(properties);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
        }

        @Test
        void oauth2ProviderWithCommonProviderName_registrationFromCommonOAuth2Provider() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("github",
                    new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "GitHub",
                            null, null, null, null)));
            var expected = CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .clientName("GitHub")
                    .build();

            // when
            var result = factory.buildClientRegistrations(properties);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
        }

        @Test
        void oauth2ProviderWithIssuerUriAndCommonProviderName_registrationFromCommonOAuth2Provider() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("github",
                    new ProviderConfig(ProviderType.OAUTH2, ISSUER_URI, "client-id", "client-secret", "GitHub",
                            null, null, null, null)));
            var expected = CommonOAuth2Provider.GITHUB.getBuilder("github")
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .clientName("GitHub")
                    .build();

            // when
            var result = factory.buildClientRegistrations(properties);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
        }

        @Test
        void oauth2ProviderWithUnknownNameAndExplicitEndpoints_explicitlyBuiltRegistration() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("forgejo",
                    new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "Forgejo",
                            "https://git.example.com/login/oauth/authorize",
                            "https://git.example.com/login/oauth/access_token",
                            "https://git.example.com/api/v1/user",
                            "login")));
            var expected = ClientRegistration.withRegistrationId("forgejo")
                    .clientId("client-id")
                    .clientSecret("client-secret")
                    .clientName("Forgejo")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}")
                    .authorizationUri("https://git.example.com/login/oauth/authorize")
                    .tokenUri("https://git.example.com/login/oauth/access_token")
                    .userInfoUri("https://git.example.com/api/v1/user")
                    .userNameAttributeName("login")
                    .build();

            // when
            var result = factory.buildClientRegistrations(properties);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(List.of(expected));
        }

        @Test
        void socialLoginDisabled_emptyList() {
            // given
            var properties = new SocialLoginProperties(false, Map.of("google",
                    new ProviderConfig(ProviderType.OIDC, null, "client-id", "client-secret", "Google",
                            null, null, null, null)));

            // when
            var result = factory.buildClientRegistrations(properties);

            // then
            assertThat(result).isEmpty();
        }

        private ClientRegistration.Builder discoveredRegistration() {
            return ClientRegistration.withRegistrationId("keycloak.example.com")
                    .clientName(ISSUER_URI)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}")
                    .scope("openid")
                    .issuerUri(ISSUER_URI)
                    .authorizationUri(ISSUER_URI + "/protocol/openid-connect/auth")
                    .tokenUri(ISSUER_URI + "/protocol/openid-connect/token")
                    .jwkSetUri(ISSUER_URI + "/protocol/openid-connect/certs")
                    .userInfoUri(ISSUER_URI + "/protocol/openid-connect/userinfo")
                    .userNameAttributeName("sub");
        }
    }
}
