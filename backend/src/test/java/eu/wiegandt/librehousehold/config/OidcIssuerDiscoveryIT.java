package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration.ProviderDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link OidcIssuerDiscovery} against a real OIDC issuer: the backend's own embedded
 * Authorization Server, which serves a real {@code /.well-known/openid-configuration}. Uses a fixed
 * port for the same reason as {@link AuthorizationServerConfigurationIT}: issuer discovery rejects a
 * discovery document whose {@code issuer} differs from the requested URI, so the configured issuer
 * must already match the server port at bean-creation time.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=54332",
        "librehousehold.security.oauth2-authorization-server.issuer=http://localhost:54332",
        "librehousehold.security.oauth2-client.redirect-uri=http://localhost:54332/login/oauth2/code/spa-backend-client",
        "librehousehold.security.oauth2-client.client-secret=test-client-secret"
})
@Import(TestcontainersConfiguration.class)
class OidcIssuerDiscoveryIT {

    private static final String ISSUER = "http://localhost:54332";

    @Autowired
    private OidcIssuerDiscovery oidcIssuerDiscovery;

    @Test
    void discover_runningIssuer_builderWithDiscoveredEndpoints() {
        // when
        var result = oidcIssuerDiscovery.discover(ISSUER)
                .clientId("client-id")
                .build();

        // then
        assertThat(result.getProviderDetails())
                .extracting(ProviderDetails::getIssuerUri,
                        ProviderDetails::getAuthorizationUri,
                        ProviderDetails::getTokenUri,
                        ProviderDetails::getJwkSetUri,
                        providerDetails -> providerDetails.getUserInfoEndpoint().getUri())
                .containsExactly(ISSUER,
                        ISSUER + "/oauth2/authorize",
                        ISSUER + "/oauth2/token",
                        ISSUER + "/oauth2/jwks",
                        ISSUER + "/userinfo");
    }
}
