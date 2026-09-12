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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;

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
        "server.port=54331",
        "librehousehold.security.oauth2-authorization-server.issuer=http://localhost:54331",
        "librehousehold.security.oauth2-client.redirect-uri=http://localhost:54331/login/oauth2/code/spa-backend-client",
        "librehousehold.security.oauth2-client.client-secret=test-client-secret",
        "librehousehold.security.rate-limit.login.max-attempts=2",
        "librehousehold.security.rate-limit.login.window-duration=1m",
        "librehousehold.security.rate-limit.sensitive-endpoints.max-attempts=2",
        "librehousehold.security.rate-limit.sensitive-endpoints.window-duration=1m"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@ExtendWith(InstancioExtension.class)
class RateLimitingIT {

    private static final String BASE_PATH = "/v1";
    private static final String XSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    private static final String XSRF_TOKEN_HEADER_NAME = "X-XSRF-TOKEN";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccountService accountService;

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
        void tooManyFailedAttempts_returns429() {
            // given
            var email = "ratelimit-" + UUID.randomUUID() + "@example.com";
            createMemberWithVerifiedAccount(email, "correct horse battery staple");
            var loginPageRequest = restTestClient.get().uri("/login").exchange().returnResult(String.class);
            var csrfToken = extractCsrfToken(loginPageRequest.getResponseBody());
            var cookies = cookiesFrom(loginPageRequest);

            // when — max-attempts is overridden to 2 above, so the 3rd attempt must be rate-limited
            submitLogin(cookies, csrfToken, email, "wrong-password");
            submitLogin(cookies, csrfToken, email, "wrong-password");
            var thirdAttempt = submitLogin(cookies, csrfToken, email, "wrong-password");

            // then
            assertThat(thirdAttempt.getStatus().value()).isEqualTo(429);
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

    private EntityExchangeResult<Void> submitLogin(Map<String, String> cookies, String csrfToken, String email, String rawPassword) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("username", email);
        form.add("password", rawPassword);
        form.add("_csrf", csrfToken);
        var spec = restTestClient.post().uri("/login").contentType(MediaType.APPLICATION_FORM_URLENCODED);
        for (var cookie : cookies.entrySet()) {
            spec = spec.cookie(cookie.getKey(), cookie.getValue());
        }
        return spec.body(form).exchange().returnResult(Void.class);
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
}
