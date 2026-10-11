package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the CORS origin allowlist wired in {@link SecurityConfig#corsConfigurationSource} (see
 * P1.5.3, Arc42 Chapter 8 control CORS1). Uses a fixed port for the same reason as
 * {@link AuthorizationServerConfigurationIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "librehousehold.security.oauth2-client.client-secret=test-client-secret",
        "librehousehold.security.cors.allowed-origins=http://allowed.example.com"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
class CorsConfigurationIT {

    private static final int SERVER_PORT = TestSocketUtils.findAvailableTcpPort();
    private static final String SERVER_BASE_URL = "http://localhost:" + SERVER_PORT;

    private static final String BASE_PATH = "/v1";
    private static final String ALLOWED_ORIGIN = "http://allowed.example.com";
    private static final String UNKNOWN_ORIGIN = "http://unknown.example.com";

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
    void preflightRequest_allowedOrigin_returnsAccessControlAllowOrigin() {
        // when
        var response = preflightRequest(ALLOWED_ORIGIN);

        // then
        assertThat(response.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(ALLOWED_ORIGIN);
    }

    @Test
    void preflightRequest_unknownOrigin_omitsAccessControlAllowOrigin() {
        // when
        var response = preflightRequest(UNKNOWN_ORIGIN);

        // then
        assertThat(response.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    private EntityExchangeResult<String> preflightRequest(String origin) {
        return restTestClient.options().uri(BASE_PATH + "/members/availability")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .returnResult(String.class);
    }
}
