package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class AuthorizationServerIT {

    private static final ParameterizedTypeReference<Map<String, Object>> DISCOVERY_DOC_TYPE =
            new ParameterizedTypeReference<>() {};

    @Autowired
    private RestTestClient restTestClient;

    @Nested
    class oidcDiscoveryDocument {

        @Test
        void returnsOk() {
            // given / when / then
            restTestClient.get()
                    .uri("/.well-known/openid-configuration")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        void issuerMatchesConfiguration() {
            // given / when / then
            restTestClient.get()
                    .uri("/.well-known/openid-configuration")
                    .exchange()
                    .expectBody(DISCOVERY_DOC_TYPE)
                    .value(body -> assertThat(body).containsEntry("issuer", "http://localhost:8080"));
        }

        @ParameterizedTest(name = "{0} contains expected values")
        @MethodSource("discoveryDocumentListClaims")
        void listClaimContainsExpectedValues(String claimKey, String[] expectedValues) {
            // given / when / then
            restTestClient.get()
                    .uri("/.well-known/openid-configuration")
                    .exchange()
                    .expectBody(DISCOVERY_DOC_TYPE)
                    .value(body -> assertThat(body)
                            .extractingByKey(claimKey)
                            .asInstanceOf(list(String.class))
                            .contains(expectedValues));
        }

        private static Stream<Arguments> discoveryDocumentListClaims() {
            return Stream.of(
                    Arguments.of("grant_types_supported",            new String[]{"authorization_code", "refresh_token"}),
                    Arguments.of("scopes_supported",                 new String[]{"openid"}),
                    Arguments.of("code_challenge_methods_supported", new String[]{"S256"})
            );
        }
    }

    @Nested
    class jwksEndpoint {

        @Test
        void isPubliclyAccessibleWithoutAuthentication() {
            // given / when / then
            restTestClient.get()
                    .uri("/oauth2/jwks")
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    class authorizationEndpoint {

        @Test
        void redirectsUnauthenticatedHtmlRequestToLoginPage() {
            // given / when
            var response = restTestClient.get()
                    .uri("/oauth2/authorize")
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                    .exchange();

            // then — path includes ;jsessionid=... when cookies are absent, so only check prefix
            response.expectStatus().isFound();
            response.expectHeader().value(HttpHeaders.LOCATION,
                    location -> assertThat(URI.create(location).getPath()).startsWith("/login"));
        }
    }

    @Nested
    class defaultSecurityChain {

        @Test
        void returnsUnauthorizedForUnauthenticatedRestRequests() {
            // given / when / then
            restTestClient.get()
                    .uri("/v1/households")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }
}
