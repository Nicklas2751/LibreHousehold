package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.household.service.AccountService;
import eu.wiegandt.librehousehold.model.EmailVerificationConfirm;
import eu.wiegandt.librehousehold.model.PasswordResetConfirm;
import eu.wiegandt.librehousehold.model.PasswordResetRequest;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

/**
 * Verifies the {@link RateLimitingFilter}/{@link RateLimitingConfig} wiring end-to-end as part of
 * the real {@link SecurityConfig#defaultSecurityFilterChain} (RATE1, Arc42 Chapter 8, P2.7 Aufgabe
 * 1/2). Overrides both rate-limit properties to a low threshold so the test does not need to send
 * dozens of requests. Uses a fixed port for the same reason as {@link AuthorizationServerConfigurationIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "librehousehold.security.oauth2-client.client-secret=test-client-secret",
        "librehousehold.security.rate-limit.login.max-attempts=2",
        "librehousehold.security.rate-limit.login.window-duration=1m",
        "librehousehold.security.rate-limit.login-per-ip.max-attempts=3",
        "librehousehold.security.rate-limit.login-per-ip.window-duration=1m",
        "librehousehold.security.rate-limit.sensitive-endpoints.max-attempts=2",
        "librehousehold.security.rate-limit.sensitive-endpoints.window-duration=1m"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@ExtendWith(InstancioExtension.class)
class RateLimitingIT {

    private static final int SERVER_PORT = TestSocketUtils.findAvailableTcpPort();
    private static final String SERVER_BASE_URL = "http://localhost:" + SERVER_PORT;

    private static final String BASE_PATH = "/v1";
    private static final String XSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    private static final String XSRF_TOKEN_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String FORWARDED_FOR_HEADER_NAME = "X-Forwarded-For";
    private static final Problem TOO_MANY_REQUESTS_PROBLEM = new Problem(429, "/problems/too-many-requests",
            "Too Many Requests", "Too many requests. Please try again later.");

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

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    private HouseholdEntity createdHousehold;

    @AfterEach
    void tearDown() {
        if (createdHousehold != null) {
            memberRepository.deleteByHouseholdId(createdHousehold.id());
            householdRepository.deleteById(createdHousehold.id());
        }
    }

    @Nested
    class login {

        @Test
        void tooManyFailedAttempts_returns429Problem() {
            // given
            var clientIp = "192.0.2.1";
            var email = "ratelimit-" + UUID.randomUUID() + "@example.com";
            createMemberWithVerifiedAccount(email, "correct horse battery staple");
            var loginPageRequest = restTestClient.get().uri("/login").exchange().returnResult(String.class);
            var csrfToken = extractCsrfToken(loginPageRequest.getResponseBody());
            var cookies = cookiesFrom(loginPageRequest);

            // when — max-attempts is overridden to 2 above, so the 3rd attempt must be rate-limited
            submitLogin(clientIp, cookies, csrfToken, email, "wrong-password");
            submitLogin(clientIp, cookies, csrfToken, email, "wrong-password");
            var thirdAttempt = submitLogin(clientIp, cookies, csrfToken, email, "wrong-password");

            // then
            assertThat(toProblem(thirdAttempt)).isEqualTo(TOO_MANY_REQUESTS_PROBLEM);
        }

        @Test
        void tooManyFailedAttempts_setsRetryAfter() {
            // given
            var clientIp = "192.0.2.2";
            var email = "ratelimit-" + UUID.randomUUID() + "@example.com";
            createMemberWithVerifiedAccount(email, "correct horse battery staple");
            var loginPageRequest = restTestClient.get().uri("/login").exchange().returnResult(String.class);
            var csrfToken = extractCsrfToken(loginPageRequest.getResponseBody());
            var cookies = cookiesFrom(loginPageRequest);

            // when
            submitLogin(clientIp, cookies, csrfToken, email, "wrong-password");
            submitLogin(clientIp, cookies, csrfToken, email, "wrong-password");
            var thirdAttempt = submitLogin(clientIp, cookies, csrfToken, email, "wrong-password");

            // then — the window is 1m, so the wait is at most 60 seconds
            assertThat(Long.parseLong(thirdAttempt.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)))
                    .isBetween(1L, 60L);
        }

        @Test
        void manyDistinctEmailsSameIp_returns429() {
            // given
            var clientIp = "192.0.2.3";
            var loginPageRequest = restTestClient.get().uri("/login").exchange().returnResult(String.class);
            var csrfToken = extractCsrfToken(loginPageRequest.getResponseBody());
            var cookies = cookiesFrom(loginPageRequest);

            // when — login-per-ip.max-attempts is overridden to 3 above, so the 4th attempt from the
            // same IP must be rate-limited, although no single e-mail reaches its own limit
            submitLogin(clientIp, cookies, csrfToken, "first-" + UUID.randomUUID() + "@example.com", "wrong-password");
            submitLogin(clientIp, cookies, csrfToken, "second-" + UUID.randomUUID() + "@example.com", "wrong-password");
            submitLogin(clientIp, cookies, csrfToken, "third-" + UUID.randomUUID() + "@example.com", "wrong-password");
            var fourthAttempt = submitLogin(clientIp, cookies, csrfToken, "fourth-" + UUID.randomUUID() + "@example.com", "wrong-password");

            // then
            assertThat(fourthAttempt.getStatus().value()).isEqualTo(429);
        }
    }

    @Nested
    class sensitiveEndpoints {

        static Stream<Arguments> requestsByPath() {
            return Stream.of(
                    Arguments.of(BASE_PATH + "/password-reset/request",
                            new PasswordResetRequest("ratelimit-" + UUID.randomUUID() + "@example.com")),
                    Arguments.of(BASE_PATH + "/password-reset/confirm",
                            new PasswordResetConfirm(UUID.randomUUID(), "correct horse battery staple")),
                    Arguments.of(BASE_PATH + "/members/verification/confirm",
                            new EmailVerificationConfirm(UUID.randomUUID())));
        }

        @ParameterizedTest
        @MethodSource("requestsByPath")
        void tooManyRequests_returns429(String path, Object requestBody) {
            // given
            var xsrfToken = fetchXsrfTokenCookieValue();

            // when — max-attempts is overridden to 2 above, so the 3rd request must be rate-limited
            postJson(path, xsrfToken, requestBody);
            postJson(path, xsrfToken, requestBody);
            var thirdAttempt = postJson(path, xsrfToken, requestBody);

            // then
            assertThat(thirdAttempt.getStatus().value()).isEqualTo(429);
        }

        /**
         * Deliberately unauthenticated: the rate limit must apply regardless of whether the request
         * would otherwise be rejected as unauthenticated (401), since resend is IP-keyed, not
         * session-keyed (see {@link RateLimitingConfig}).
         */
        @Test
        void resendVerificationEmail_tooManyRequests_returns429() {
            // given
            var xsrfToken = fetchXsrfTokenCookieValue();
            var path = BASE_PATH + "/household/" + UUID.randomUUID() + "/members/" + UUID.randomUUID() + "/verification/resend";

            // when
            postWithoutBody(path, xsrfToken);
            postWithoutBody(path, xsrfToken);
            var thirdAttempt = postWithoutBody(path, xsrfToken);

            // then
            assertThat(thirdAttempt.getStatus().value()).isEqualTo(429);
        }

        private String fetchXsrfTokenCookieValue() {
            var response = restTestClient.get().uri(BASE_PATH + "/members/availability?email=" + UUID.randomUUID() + "%40example.com")
                    .exchange().returnResult(String.class);
            return response.getResponseCookies().getFirst(XSRF_TOKEN_COOKIE_NAME).getValue();
        }

        private EntityExchangeResult<String> postJson(String path, String xsrfToken, Object body) {
            return restTestClient.post().uri(path)
                    .cookie(XSRF_TOKEN_COOKIE_NAME, xsrfToken)
                    .header(XSRF_TOKEN_HEADER_NAME, xsrfToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange().returnResult(String.class);
        }

        private EntityExchangeResult<String> postWithoutBody(String path, String xsrfToken) {
            return restTestClient.post().uri(path)
                    .cookie(XSRF_TOKEN_COOKIE_NAME, xsrfToken)
                    .header(XSRF_TOKEN_HEADER_NAME, xsrfToken)
                    .exchange().returnResult(String.class);
        }
    }

    // server.forward-headers-strategy=framework takes the client IP from X-Forwarded-For, so each test
    // gets its own per-IP bucket and stays independent of the others
    private EntityExchangeResult<String> submitLogin(String clientIp, Map<String, String> cookies, String csrfToken, String email, String rawPassword) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("username", email);
        form.add("password", rawPassword);
        form.add("_csrf", csrfToken);
        var spec = restTestClient.post().uri("/login")
                .header(FORWARDED_FOR_HEADER_NAME, clientIp)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        for (var cookie : cookies.entrySet()) {
            spec = spec.cookie(cookie.getKey(), cookie.getValue());
        }
        return spec.body(form).exchange().returnResult(String.class);
    }

    private Map<String, String> cookiesFrom(EntityExchangeResult<String> response) {
        var cookies = new LinkedHashMap<String, String>();
        response.getResponseCookies().values().stream()
                .flatMap(List::stream)
                .forEach(cookie -> cookies.put(cookie.getName(), cookie.getValue()));
        return cookies;
    }

    private void createMemberWithVerifiedAccount(String email, String rawPassword) {
        createdHousehold = householdRepository.save(Instancio.create(HouseholdEntity.class));
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), createdHousehold.id())
                .set(field(MemberEntity::email), email)
                .create());
        accountService.createAccount(member.getId(), rawPassword);
        accountService.markEmailVerified(member.getId());
    }

    private String extractCsrfToken(String loginPageHtml) {
        var csrfInputStart = loginPageHtml.indexOf("name=\"_csrf\"");
        var inputTag = loginPageHtml.substring(csrfInputStart, loginPageHtml.indexOf('>', csrfInputStart));
        var matcher = Pattern.compile("value=\"([^\"]*)\"").matcher(inputTag);
        matcher.find();
        return matcher.group(1);
    }

    private Problem toProblem(EntityExchangeResult<String> response) {
        var body = objectMapper.readTree(response.getResponseBody());
        return new Problem(response.getStatus().value(), body.path("type").asString(), body.path("title").asString(),
                body.path("detail").asString());
    }

    private record Problem(int status, String type, String title, String detail) {
    }
}
