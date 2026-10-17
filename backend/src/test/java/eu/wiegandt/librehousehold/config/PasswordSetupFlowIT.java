package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives household setup with a password the way a fresh browser does: the SPA's own login reached
 * /oauth2/authorize first, which saved that request before sending the user to /login. Uses a port
 * picked before startup for the reason given in {@link CsrfProtectionIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "librehousehold.security.oauth2-client.client-secret=test-client-secret"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
class PasswordSetupFlowIT {

    private static final int SERVER_PORT = TestSocketUtils.findAvailableTcpPort();
    private static final String SERVER_BASE_URL = "http://localhost:" + SERVER_PORT;

    private static final String BASE_PATH = "/v1";
    private static final String SPA_LOGIN_PATH = "/oauth2/authorization/spa-backend-client";
    private static final String XSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    private static final String XSRF_TOKEN_HEADER_NAME = "X-XSRF-TOKEN";
    private static final Map<String, String> NO_COOKIES = Map.of();
    private static final int MAX_REDIRECTS = 10;

    @DynamicPropertySource
    static void serverPortProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> SERVER_PORT);
        registry.add("librehousehold.security.oauth2-authorization-server.issuer", () -> SERVER_BASE_URL);
        registry.add("librehousehold.security.oauth2-client.redirect-uri",
                () -> SERVER_BASE_URL + "/login/oauth2/code/spa-backend-client");
    }

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InviteRepository inviteRepository;

    private UUID createdHouseholdId;

    @AfterEach
    void tearDown() {
        if (createdHouseholdId != null) {
            inviteRepository.deleteByHouseholdId(createdHouseholdId);
            memberRepository.deleteByHouseholdId(createdHouseholdId);
            householdRepository.deleteById(createdHouseholdId);
        }
    }

    @Nested
    class householdSetup {

        /**
         * The stale request's authorization request no longer exists once the SPA started a fresh
         * one, so resuming it would end on the login error page.
         */
        @Test
        void startedFromSpaAuthorization_spaLoginEndsAtDefaultTargetInsteadOfStaleAuthorizationRequest() {
            // given
            createdHouseholdId = UUID.randomUUID();
            var spaAuthorization = get(NO_COOKIES, URI.create(SPA_LOGIN_PATH));
            var staleAuthorize = get(spaAuthorization.cookies(), spaAuthorization.response().getResponseHeaders().getLocation());
            var csrfPreflight = get(staleAuthorize.cookies(), URI.create(BASE_PATH + "/members/availability?email=local%40example.com"));
            var setupBody = """
                    {"household": {"id": "%s", "name": "Password Household"},
                     "member": {"id": "%s", "name": "Max Mustermann", "email": "local-%s@example.com"},
                     "localRegistration": {"password": "correct horse battery staple"}}
                    """.formatted(createdHouseholdId, UUID.randomUUID(), UUID.randomUUID());
            var setup = post(csrfPreflight.cookies(), BASE_PATH + "/household/setup", setupBody);

            // when
            var finalRedirect = followBackendRedirects(get(setup.cookies(), URI.create(SPA_LOGIN_PATH)));

            // then
            assertThat(finalRedirect).hasPath("/").hasNoQuery();
        }
    }

    /**
     * Follows the redirects within the backend's own OAuth2 endpoints and returns the first
     * redirect target outside them, i.e. where the browser finally lands.
     */
    private URI followBackendRedirects(HttpStep start) {
        var step = start;
        var location = step.response().getResponseHeaders().getLocation();
        for (var redirects = 0; redirects < MAX_REDIRECTS && isBackendOAuth2Endpoint(location); redirects++) {
            step = get(step.cookies(), location);
            location = step.response().getResponseHeaders().getLocation();
        }
        return location;
    }

    private boolean isBackendOAuth2Endpoint(URI location) {
        return location != null
                && (location.getPath().startsWith("/oauth2/") || location.getPath().startsWith("/login/oauth2/"));
    }

    private HttpStep get(Map<String, String> cookies, URI uri) {
        var response = applyCookies(restTestClient.get().uri(uri), cookies).exchange().returnResult(String.class);
        return new HttpStep(mergeCookies(cookies, response), response);
    }

    private HttpStep post(Map<String, String> cookies, String uri, String jsonBody) {
        var response = applyCookies(restTestClient.post().uri(uri), cookies)
                .header(XSRF_TOKEN_HEADER_NAME, cookies.get(XSRF_TOKEN_COOKIE_NAME))
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .exchange().returnResult(String.class);
        return new HttpStep(mergeCookies(cookies, response), response);
    }

    private <S extends RestTestClient.RequestHeadersSpec<?>> S applyCookies(S spec, Map<String, String> cookies) {
        for (var cookie : cookies.entrySet()) {
            spec.cookie(cookie.getKey(), cookie.getValue());
        }
        return spec;
    }

    private Map<String, String> mergeCookies(Map<String, String> existingCookies, ExchangeResult response) {
        var merged = new LinkedHashMap<>(existingCookies);
        response.getResponseCookies().values().stream()
                .flatMap(List::stream)
                .forEach(cookie -> merged.put(cookie.getName(), cookie.getValue()));
        return merged;
    }

    private record HttpStep(Map<String, String> cookies, EntityExchangeResult<String> response) {
    }
}
