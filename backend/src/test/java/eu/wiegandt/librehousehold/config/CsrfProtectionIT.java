package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the cookie-based CSRF wiring required by ADR-014 (P1.5.2): {@code XSRF-TOKEN} must be
 * readable by JavaScript (no {@code HttpOnly}) and accepted back via the {@code X-XSRF-TOKEN}
 * header, as opposed to Spring Security's session-based CSRF default (which never exposes a
 * cookie at all). Uses a fixed port for the same reason as {@link AuthorizationServerConfigurationIT}:
 * {@link SecurityConfig} needs a resolvable {@code issuer}/{@code redirect-uri} at bean-creation
 * time, even though this test never drives the OAuth2 flow itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "librehousehold.security.oauth2-client.client-secret=test-client-secret"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
class CsrfProtectionIT {

    private static final int SERVER_PORT = TestSocketUtils.findAvailableTcpPort();
    private static final String SERVER_BASE_URL = "http://localhost:" + SERVER_PORT;

    private static final String BASE_PATH = "/v1";
    private static final String XSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    private static final String XSRF_TOKEN_HEADER_NAME = "X-XSRF-TOKEN";
    // permitAll (see SecurityConfig#defaultSecurityFilterChain), so no session is needed to reach it,
    // isolating these assertions to CSRF behavior only, independent of authentication.
    private static final String PUBLIC_POST_URI = BASE_PATH + "/household/setup";
    private static final String PUBLIC_GET_URI = BASE_PATH + "/members/availability?email=csrf-test@example.com";

    // SecurityConfig builds the internal client's endpoints from the issuer at bean creation, so the
    // issuer must already contain the actual port before the context starts
    @DynamicPropertySource
    static void serverPortProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> SERVER_PORT);
        registry.add("librehousehold.security.oauth2-authorization-server.issuer", () -> SERVER_BASE_URL);
        registry.add("librehousehold.security.oauth2-client.redirect-uri",
                () -> SERVER_BASE_URL + "/login/oauth2/code/spa-backend-client");
    }

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void stateChangingRequest_missingCsrfHeader_returns403() {
        // when
        var response = restTestClient.post().uri(PUBLIC_POST_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .returnResult(String.class);

        // then
        assertThat(response.getStatus().value()).isEqualTo(403);
    }

    @Test
    void stateChangingRequest_validCsrfHeaderFromCookie_doesNotFailWithCsrfError() {
        // given
        var xsrfToken = fetchXsrfTokenCookieValue();

        // when
        var response = restTestClient.post().uri(PUBLIC_POST_URI)
                .cookie(XSRF_TOKEN_COOKIE_NAME, xsrfToken)
                .header(XSRF_TOKEN_HEADER_NAME, xsrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .returnResult(String.class);

        // then
        assertThat(response.getStatus().value()).isNotEqualTo(403);
    }

    @Test
    void getRequest_anyState_returnsReadableXsrfTokenCookie() {
        // when
        var response = restTestClient.get().uri(PUBLIC_GET_URI).exchange().returnResult(String.class);

        // then
        assertThat(response.getResponseHeaders().get("Set-Cookie"))
                .anySatisfy(setCookieHeader -> assertThat(setCookieHeader)
                        .contains(XSRF_TOKEN_COOKIE_NAME + "=")
                        .doesNotContainIgnoringCase("HttpOnly"));
    }

    /**
     * Reproduces the deployment topology from Kapitel 7 (Nginx terminates TLS, forwards internally
     * over plain HTTP): without {@code server.forward-headers-strategy}, Spring ignores
     * {@code X-Forwarded-Proto} and {@link CookieCsrfTokenRepository} derives the {@code Secure} flag
     * from {@code request.isSecure()}, which would then wrongly report the request as insecure.
     */
    @Test
    void getRequest_forwardedProtoHttps_returnsSecureXsrfTokenCookie() {
        // when
        var response = restTestClient.get().uri(PUBLIC_GET_URI)
                .header("X-Forwarded-Proto", "https")
                .exchange()
                .returnResult(String.class);

        // then
        assertThat(response.getResponseHeaders().get("Set-Cookie"))
                .anySatisfy(setCookieHeader -> assertThat(setCookieHeader)
                        .contains(XSRF_TOKEN_COOKIE_NAME + "=")
                        .contains("Secure"));
    }

    private String fetchXsrfTokenCookieValue() {
        var response = restTestClient.get().uri(PUBLIC_GET_URI).exchange().returnResult(String.class);
        return response.getResponseCookies().getFirst(XSRF_TOKEN_COOKIE_NAME).getValue();
    }
}
