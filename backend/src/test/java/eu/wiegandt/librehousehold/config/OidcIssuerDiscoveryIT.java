package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration.ProviderDetails;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link OidcIssuerDiscovery} against a real OIDC issuer: the backend's own embedded
 * Authorization Server, which serves a real {@code /.well-known/openid-configuration}. Uses a fixed
 * port for the same reason as {@link AuthorizationServerConfigurationIT}: issuer discovery rejects a
 * discovery document whose {@code issuer} differs from the requested URI, so the configured issuer
 * must already match the server port at bean-creation time.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "librehousehold.security.oauth2-client.client-secret=test-client-secret"
})
@Import(TestcontainersConfiguration.class)
class OidcIssuerDiscoveryIT {

    private static final int SERVER_PORT = TestSocketUtils.findAvailableTcpPort();
    private static final String ISSUER = "http://localhost:" + SERVER_PORT;

    // SecurityConfig builds the internal client's endpoints from the issuer at bean creation, so the
    // issuer must already contain the actual port before the context starts
    @DynamicPropertySource
    static void serverPortProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> SERVER_PORT);
        registry.add("librehousehold.security.oauth2-authorization-server.issuer", () -> ISSUER);
        registry.add("librehousehold.security.oauth2-client.redirect-uri",
                () -> ISSUER + "/login/oauth2/code/spa-backend-client");
    }

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
