package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.PasswordReset;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.household.service.AccountService;
import eu.wiegandt.librehousehold.household.service.AccountTokenService;
import org.instancio.Instancio;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

/**
 * Drives the form login and logout like the SPA does (see ADR-013): an XSRF-TOKEN cookie from a GET,
 * then {@code POST /v1/login} as form data with the field {@code email}. Runs the real server, so
 * the real {@code Set-Cookie} attributes and session-ID changes are visible. {@code RestTestClient}
 * keeps no cookie jar, so each test threads the cookies itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Every test logs in from the same IP; the per-IP login limit is covered by RateLimitingIT
        "librehousehold.security.rate-limit.login-per-ip.max-attempts=10000",
        "librehousehold.security.rate-limit.login.max-attempts=10000"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
class FormLoginIT {

    private static final String BASE_PATH = "/v1";
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String SESSION_COOKIE = "JSESSIONID";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";
    private static final Map<String, String> NO_COOKIES = Map.of();

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountTokenService accountTokenService;

    @Autowired
    private PasswordReset passwordResetService;

    @Autowired
    private JsonMapper jsonMapper;

    @Nested
    class login {

        @Test
        void validCredentials_returns204() {
            // given
            var email = uniqueEmail();
            createVerifiedMember(email);

            // when
            var response = login(xsrfCookies(), email, RAW_PASSWORD);

            // then
            assertThat(response.getStatus().value()).isEqualTo(204);
        }

        @Test
        void validCredentials_meReturns200() {
            // given
            var email = uniqueEmail();
            createVerifiedMember(email);
            var cookies = xsrfCookies();
            var sessionCookies = merge(cookies, login(cookies, email, RAW_PASSWORD));

            // when
            var response = get(sessionCookies, BASE_PATH + "/me");

            // then
            assertThat(response.getStatus().value()).isEqualTo(200);
        }

        @Test
        void oldSessionId_rejected() {
            // given
            var email = uniqueEmail();
            createVerifiedMember(email);
            var anonymousSession = anonymousSessionCookies();
            var sessionCookies = merge(anonymousSession, login(anonymousSession, email, RAW_PASSWORD));
            var oldSessionCookies = new LinkedHashMap<>(sessionCookies);
            oldSessionCookies.put(SESSION_COOKIE, anonymousSession.get(SESSION_COOKIE));

            // when
            var response = get(oldSessionCookies, BASE_PATH + "/me");

            // then
            assertThat(response.getStatus().value()).isEqualTo(401);
        }

        @Test
        void newSessionIdDiffersFromOld() {
            // given
            var email = uniqueEmail();
            createVerifiedMember(email);
            var anonymousSession = anonymousSessionCookies();

            // when
            var sessionCookies = merge(anonymousSession, login(anonymousSession, email, RAW_PASSWORD));

            // then
            assertThat(sessionCookies.get(SESSION_COOKIE)).isNotEqualTo(anonymousSession.get(SESSION_COOKIE));
        }

        @Test
        void responseSetsNewXsrfCookie() {
            // given
            var email = uniqueEmail();
            createVerifiedMember(email);
            var cookies = xsrfCookies();

            // when
            var response = login(cookies, email, RAW_PASSWORD);

            // then
            assertThat(cookieValue(response, XSRF_COOKIE)).isNotBlank().isNotEqualTo(cookies.get(XSRF_COOKIE));
        }

        @Test
        void postDirectlyAfterLogin_notForbidden() {
            // given
            var email = uniqueEmail();
            createVerifiedMember(email);
            var cookies = xsrfCookies();
            var sessionCookies = merge(cookies, login(cookies, email, RAW_PASSWORD));

            // when
            var response = postJson(sessionCookies, BASE_PATH + "/auth/social-context", "{\"context\":\"LOGIN\"}");

            // then
            assertThat(response.getStatus().value()).isEqualTo(204);
        }

        @Test
        void wrongPassword_returns401InvalidCredentials() {
            // given
            var email = uniqueEmail();
            createVerifiedMember(email);
            var expected = new Problem("/problems/invalid-credentials", 401);

            // when
            var response = login(xsrfCookies(), email, "wrong password");

            // then
            assertThat(problemOf(response)).isEqualTo(expected);
        }

        @Test
        void unknownEmail_returns401InvalidCredentials() {
            // given
            var expected = new Problem("/problems/invalid-credentials", 401);

            // when
            var response = login(xsrfCookies(), uniqueEmail(), RAW_PASSWORD);

            // then
            assertThat(problemOf(response)).isEqualTo(expected);
        }

        @Test
        void unverifiedCorrectPassword_returns403EmailNotVerified() {
            // given
            var email = uniqueEmail();
            createMember(email);
            var expected = new Problem("/problems/email-not-verified", 403);

            // when
            var response = login(xsrfCookies(), email, RAW_PASSWORD);

            // then
            assertThat(problemOf(response)).isEqualTo(expected);
        }

        @Test
        void lockedAccount_returnsLockedProblem() {
            // given
            var email = uniqueEmail();
            var member = createVerifiedMember(email);
            var lockedUntil = Instant.now().plusSeconds(900).truncatedTo(ChronoUnit.SECONDS);
            accountRepository.lockUntil(member.getId(), lockedUntil);
            var expected = new LockedProblem("/problems/account-locked", 403, lockedUntil);

            // when
            var response = login(xsrfCookies(), email, RAW_PASSWORD);

            // then
            assertThat(parse(response, LockedProblem.class)).isEqualTo(expected);
        }

        @Test
        void lockedAccount_setsRetryAfter() {
            // given
            var email = uniqueEmail();
            var member = createVerifiedMember(email);
            accountRepository.lockUntil(member.getId(), Instant.now().plusSeconds(900));

            // when
            var response = login(xsrfCookies(), email, RAW_PASSWORD);

            // then
            assertThat(response.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isIn("899", "900");
        }

        @Test
        void missingCsrfHeader_returns403() {
            // given
            var email = uniqueEmail();
            createVerifiedMember(email);
            var form = new LinkedMultiValueMap<String, String>();
            form.add("email", email);
            form.add("password", RAW_PASSWORD);

            // when
            var response = withCookies(restTestClient.post().uri(BASE_PATH + "/login"), xsrfCookies())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange()
                    .returnResult(String.class);

            // then
            assertThat(response.getStatus().value()).isEqualTo(403);
        }
    }

    @Nested
    class logout {

        @Test
        void authenticatedSession_returns204() {
            // given
            var sessionCookies = loggedInCookies();

            // when
            var response = post(sessionCookies, BASE_PATH + "/logout");

            // then
            assertThat(response.getStatus().value()).isEqualTo(204);
        }

        @Test
        void oldSessionCookieReplayedAfterLogout_returns401() {
            // given
            var sessionCookies = loggedInCookies();
            post(sessionCookies, BASE_PATH + "/logout");

            // when
            var response = get(sessionCookies, BASE_PATH + "/me");

            // then
            assertThat(response.getStatus().value()).isEqualTo(401);
        }

        @Test
        void responseSetsNewXsrfCookie() {
            // given
            var sessionCookies = loggedInCookies();

            // when
            var response = post(sessionCookies, BASE_PATH + "/logout");

            // then
            assertThat(cookieValue(response, XSRF_COOKIE)).isNotBlank().isNotEqualTo(sessionCookies.get(XSRF_COOKIE));
        }

        @Test
        void thenLoginWithSameCookieJar_returns204() {
            // given
            var email = uniqueEmail();
            createVerifiedMember(email);
            var cookies = xsrfCookies();
            var sessionCookies = merge(cookies, login(cookies, email, RAW_PASSWORD));
            var loggedOutCookies = merge(sessionCookies, post(sessionCookies, BASE_PATH + "/logout"));

            // when
            var response = login(loggedOutCookies, email, RAW_PASSWORD);

            // then
            assertThat(response.getStatus().value()).isEqualTo(204);
        }
    }

    @Nested
    class sessionCookie {

        /**
         * Lax, not Strict: the social-login callback is a cross-site top-level GET that must carry
         * the session cookie (ADR-014).
         */
        @Test
        void afterLogin_isSecureHttpOnlySameSiteLax() {
            // given
            var email = uniqueEmail();
            createVerifiedMember(email);

            // when
            var response = login(xsrfCookies(), email, RAW_PASSWORD);

            // then
            assertThat(response.getResponseHeaders().getOrEmpty(HttpHeaders.SET_COOKIE))
                    .filteredOn(header -> header.startsWith(SESSION_COOKIE + "="))
                    .isNotEmpty()
                    .allSatisfy(header -> assertThat(header)
                            .contains("Secure")
                            .containsIgnoringCase("HttpOnly")
                            .contains("SameSite=Lax"));
        }
    }

    @Nested
    class passwordReset {

        @Test
        void sessionFromFormLogin_rejected() {
            // given
            var email = uniqueEmail();
            var member = createVerifiedMember(email);
            var cookies = xsrfCookies();
            var sessionCookies = merge(cookies, login(cookies, email, RAW_PASSWORD));
            var resetToken = accountTokenService.issuePasswordResetToken(member.getId());
            passwordResetService.confirmPasswordReset(resetToken, "new correct horse battery staple");

            // when
            var response = get(sessionCookies, BASE_PATH + "/me");

            // then
            assertThat(response.getStatus().value()).isEqualTo(401);
        }
    }

    /**
     * The principal is identified by the member ID, not by the e-mail address, so changing the
     * address must not orphan the session.
     */
    @Nested
    class emailChange {

        @Test
        void sessionStaysValid() {
            // given
            var email = uniqueEmail();
            var member = createVerifiedMember(email);
            var cookies = xsrfCookies();
            var sessionCookies = merge(cookies, login(cookies, email, RAW_PASSWORD));
            var memberUri = BASE_PATH + "/household/" + member.householdId() + "/members/" + member.getId();
            var changedCookies = merge(sessionCookies,
                    patchJson(sessionCookies, memberUri, "{\"email\":\"" + uniqueEmail() + "\"}"));

            // when
            var response = get(changedCookies, BASE_PATH + "/me");

            // then
            assertThat(response.getStatus().value()).isEqualTo(200);
        }
    }

    @Test
    void protectedBusinessEndpoint_noSession_returns401() {
        // when
        var response = get(NO_COOKIES, BASE_PATH + "/household/" + UUID.randomUUID() + "/tasks");

        // then
        assertThat(response.getStatus().value()).isEqualTo(401);
    }

    @Test
    void me_noSession_returns401() {
        // when
        var response = get(NO_COOKIES, BASE_PATH + "/me");

        // then
        assertThat(response.getStatus().value()).isEqualTo(401);
    }

    /**
     * Guards against a permitAll() entry without the base-path prefix, which never matches a real request.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/members/availability?email=someone%40example.com",
            "/auth/discover",
            "/auth/social-registration",
            "/invite/9b2d6f0e-1f6a-4b7c-8d3e-2a4f5b6c7d8e"})
    void publicEndpoints_noSession_reachable(String path) {
        // when
        var response = get(NO_COOKIES, BASE_PATH + path);

        // then
        assertThat(response.getStatus().value()).isNotIn(401, 403);
    }

    private Map<String, String> loggedInCookies() {
        var email = uniqueEmail();
        createVerifiedMember(email);
        var cookies = xsrfCookies();
        return merge(cookies, login(cookies, email, RAW_PASSWORD));
    }

    private Map<String, String> xsrfCookies() {
        return merge(NO_COOKIES, get(NO_COOKIES, BASE_PATH + "/auth/discover"));
    }

    // Storing a social-login context is the SPA's only way to get a session before logging in
    private Map<String, String> anonymousSessionCookies() {
        var cookies = xsrfCookies();
        return merge(cookies, postJson(cookies, BASE_PATH + "/auth/social-context", "{\"context\":\"LOGIN\"}"));
    }

    private EntityExchangeResult<String> login(Map<String, String> cookies, String email, String password) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("email", email);
        form.add("password", password);
        return withCookies(restTestClient.post().uri(BASE_PATH + "/login"), cookies)
                .header(XSRF_HEADER, cookies.get(XSRF_COOKIE))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange()
                .returnResult(String.class);
    }

    private EntityExchangeResult<String> get(Map<String, String> cookies, String uri) {
        return withCookies(restTestClient.get().uri(uri), cookies)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .returnResult(String.class);
    }

    private EntityExchangeResult<String> post(Map<String, String> cookies, String uri) {
        return withCookies(restTestClient.post().uri(uri), cookies)
                .header(XSRF_HEADER, cookies.get(XSRF_COOKIE))
                .exchange()
                .returnResult(String.class);
    }

    private EntityExchangeResult<String> postJson(Map<String, String> cookies, String uri, String json) {
        return withCookies(restTestClient.post().uri(uri), cookies)
                .header(XSRF_HEADER, cookies.get(XSRF_COOKIE))
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange()
                .returnResult(String.class);
    }

    private EntityExchangeResult<String> patchJson(Map<String, String> cookies, String uri, String json) {
        return withCookies(restTestClient.patch().uri(uri), cookies)
                .header(XSRF_HEADER, cookies.get(XSRF_COOKIE))
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange()
                .returnResult(String.class);
    }

    private <S extends RestTestClient.RequestHeadersSpec<?>> S withCookies(S spec, Map<String, String> cookies) {
        cookies.forEach(spec::cookie);
        return spec;
    }

    private Map<String, String> merge(Map<String, String> cookies, ExchangeResult response) {
        var merged = new LinkedHashMap<>(cookies);
        response.getResponseCookies().values().stream()
                .flatMap(List::stream)
                .forEach(cookie -> merged.put(cookie.getName(), cookie.getValue()));
        return merged;
    }

    // A renewed token comes as two Set-Cookie headers: the removal of the old one, then the new one
    private String cookieValue(ExchangeResult response, String name) {
        var cookies = response.getResponseCookies().getOrDefault(name, List.of());
        return cookies.isEmpty() ? null : cookies.getLast().getValue();
    }

    private Problem problemOf(EntityExchangeResult<String> response) {
        return parse(response, Problem.class);
    }

    private <T> T parse(EntityExchangeResult<String> response, Class<T> type) {
        return jsonMapper.readValue(response.getResponseBody(), type);
    }

    private MemberEntity createVerifiedMember(String email) {
        var member = createMember(email);
        accountRepository.markEmailVerified(member.getId());
        return member;
    }

    private MemberEntity createMember(String email) {
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .set(field(MemberEntity::email), email)
                .create());
        accountService.createAccount(member.getId(), RAW_PASSWORD);
        return member;
    }

    private static String uniqueEmail() {
        return "formlogin-" + UUID.randomUUID() + "@example.com";
    }

    private record Problem(String type, int status) {
    }

    private record LockedProblem(String type, int status, Instant lockedUntil) {
    }
}
