package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderConfig;
import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderType;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Nested
    class clientRegistrationRepository {

        @Test
        void socialLoginDisabled_onlySpaClientRegistration() {
            // given
            var socialLoginProperties = new SocialLoginProperties(false, Map.of());

            // when
            var repository = securityConfig.clientRegistrationRepository(
                    "http://localhost:8080", "http://localhost:8080/oauth2/authorize",
                    "http://localhost:8080/login/oauth2/code/spa-backend-client", "client-secret",
                    socialLoginProperties,
                    new SocialLoginClientRegistrationFactory(new CommonProvider(), new OidcIssuerDiscovery()),
                    new SocialLoginConfigurationValidator(new CommonProvider()));

            // then
            assertThat(repository).asInstanceOf(InstanceOfAssertFactories.iterable(ClientRegistration.class))
                    .extracting(ClientRegistration::getRegistrationId)
                    .containsExactly(RegisteredClientSeeder.CLIENT_ID);
        }

        @Test
        void socialLoginEnabledWithCommonProvider_spaAndProviderClientRegistrations() {
            // given
            var socialLoginProperties = new SocialLoginProperties(true, Map.of("github",
                    new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "GitHub",
                            null, null, null, null)));

            // when
            var repository = securityConfig.clientRegistrationRepository(
                    "http://localhost:8080", "http://localhost:8080/oauth2/authorize",
                    "http://localhost:8080/login/oauth2/code/spa-backend-client", "client-secret",
                    socialLoginProperties,
                    new SocialLoginClientRegistrationFactory(new CommonProvider(), new OidcIssuerDiscovery()),
                    new SocialLoginConfigurationValidator(new CommonProvider()));

            // then
            assertThat(repository).asInstanceOf(InstanceOfAssertFactories.iterable(ClientRegistration.class))
                    .extracting(ClientRegistration::getRegistrationId)
                    .containsExactlyInAnyOrder(RegisteredClientSeeder.CLIENT_ID, "github");
        }

        @Test
        void invalidSocialLoginConfiguration_throwsIllegalStateException() {
            // given
            var socialLoginProperties = new SocialLoginProperties(true, Map.of("keycloak-self-hosted",
                    new ProviderConfig(ProviderType.OIDC, null, "client-id", "client-secret", "Keycloak",
                            null, null, null, null)));
            var clientRegistrationFactory =
                    new SocialLoginClientRegistrationFactory(new CommonProvider(), new OidcIssuerDiscovery());
            var validator = new SocialLoginConfigurationValidator(new CommonProvider());

            // when / then
            assertThatThrownBy(() -> securityConfig.clientRegistrationRepository(
                    "http://localhost:8080", "http://localhost:8080/oauth2/authorize",
                    "http://localhost:8080/login/oauth2/code/spa-backend-client", "client-secret",
                    socialLoginProperties, clientRegistrationFactory, validator))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("keycloak-self-hosted");
        }
    }
}
