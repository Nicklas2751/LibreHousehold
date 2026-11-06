package eu.wiegandt.librehousehold.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import static org.assertj.core.api.Assertions.assertThat;

class FederatedProviderKeyTest {

    @Nested
    class of {

        @Test
        void oidc_returnsIssuer() {
            // given
            var registration = registration("https://issuer.example.com/realms/home",
                    "https://issuer.example.com/userinfo");

            // when
            var key = FederatedProviderKey.of(registration);

            // then
            assertThat(key).isEqualTo("https://issuer.example.com/realms/home");
        }

        @Test
        void oauth2_returnsUserInfoUriWithoutQueryAndFragment() {
            // given
            var registration = registration(null, "https://api.example.com/user?fields=id#profile");

            // when
            var key = FederatedProviderKey.of(registration);

            // then
            assertThat(key).isEqualTo("https://api.example.com/user");
        }

        /**
         * Two providers can be hosted on the same origin, e.g. two tenants of one service.
         */
        @Test
        void oauth2SameOriginDifferentPath_differentKeys() {
            // given
            var tenantA = registration(null, "https://api.example.com/tenant-a/user");
            var tenantB = registration(null, "https://api.example.com/tenant-b/user");

            // when
            var keyOfTenantA = FederatedProviderKey.of(tenantA);

            // then
            assertThat(keyOfTenantA).isNotEqualTo(FederatedProviderKey.of(tenantB));
        }
    }

    private ClientRegistration registration(String issuerUri, String userInfoUri) {
        return ClientRegistration.withRegistrationId("provider")
                .clientId("client-id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://provider.example.com/authorize")
                .tokenUri("https://provider.example.com/token")
                .issuerUri(issuerUri)
                .userInfoUri(userInfoUri)
                .build();
    }
}
