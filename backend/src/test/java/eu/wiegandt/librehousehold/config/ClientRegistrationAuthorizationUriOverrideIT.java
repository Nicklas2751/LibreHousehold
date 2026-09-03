package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the browser-vs-server-to-server URI split fixed in
 * {@link SecurityConfig#clientRegistrationRepository}: {@code authorizationUri} is the only one of
 * the four OAuth2 endpoint URIs the browser actually navigates to (via the Vite dev proxy in local
 * development), while {@code tokenUri}/{@code jwkSetUri}/{@code userInfoUri} are called exclusively
 * by the backend itself and must keep resolving against {@code issuer} directly — routing those
 * server-to-server calls through the dev proxy as well caused the code-exchange step to hang
 * indefinitely (connection pool exhaustion in the proxy's Node process). No fixed port needed here
 * (unlike {@link AuthorizationServerConfigurationIT}/{@link CorsConfigurationIT}): this test never
 * issues an HTTP request, it only inspects the configured {@link ClientRegistrationRepository} bean.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "librehousehold.security.oauth2-client.client-secret=test-client-secret",
        "librehousehold.security.oauth2-client.authorization-uri=http://localhost:5173/oauth2/authorize"
})
@Import(TestcontainersConfiguration.class)
class ClientRegistrationAuthorizationUriOverrideIT {

    private static final String CLIENT_ID = "spa-backend-client";
    // application.yaml default, unaffected by the authorization-uri override above.
    private static final String ISSUER = "http://localhost:8080";

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void findByRegistrationId_authorizationUriOverridden_authorizationUriUsesOverrideOthersStayOnIssuer() {
        // given
        var expected = new ConfiguredUris("http://localhost:5173/oauth2/authorize",
                ISSUER + "/oauth2/token", ISSUER + "/oauth2/jwks", ISSUER + "/userinfo");

        // when
        var providerDetails = clientRegistrationRepository.findByRegistrationId(CLIENT_ID).getProviderDetails();
        var result = new ConfiguredUris(providerDetails.getAuthorizationUri(), providerDetails.getTokenUri(),
                providerDetails.getJwkSetUri(), providerDetails.getUserInfoEndpoint().getUri());

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    private record ConfiguredUris(String authorizationUri, String tokenUri, String jwkSetUri, String userInfoUri) {
    }
}
