package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.PasswordReset;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.service.AccountTokenService;
import eu.wiegandt.librehousehold.model.HouseholdSetupResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives household setup and invite join with a password the way the SPA does: the new member is
 * signed in right after the registration, with a new session ID and a new XSRF-TOKEN (ADR-013).
 * Before the registration, the browser already has a session (here from storing a social-login
 * context), so the tests can prove that the old session ID is no longer accepted.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
class PasswordSetupFlowIT {

    private static final String BASE_PATH = "/v1";
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String SESSION_COOKIE = "JSESSIONID";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";
    private static final Map<String, String> NO_COOKIES = Map.of();

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountTokenService accountTokenService;

    @Autowired
    private PasswordReset passwordReset;

    @Nested
    class householdSetup {

        @Test
        void localRegistration_meReturns200() {
            // given
            var anonymousSession = anonymousSessionCookies();
            var setupCookies = merge(anonymousSession, setup(anonymousSession, UUID.randomUUID()));

            // when
            var response = get(setupCookies, BASE_PATH + "/me");

            // then
            assertThat(response.getStatus().value()).isEqualTo(200);
        }

        @Test
        void localRegistration_oldSessionIdRejected() {
            // given
            var anonymousSession = anonymousSessionCookies();
            var setupCookies = merge(anonymousSession, setup(anonymousSession, UUID.randomUUID()));
            var oldSessionCookies = new LinkedHashMap<>(setupCookies);
            oldSessionCookies.put(SESSION_COOKIE, anonymousSession.get(SESSION_COOKIE));

            // when
            var response = get(oldSessionCookies, BASE_PATH + "/me");

            // then
            assertThat(response.getStatus().value()).isEqualTo(401);
        }

        @Test
        void localRegistration_postDirectlyAfterSetup_notForbidden() {
            // given
            var anonymousSession = anonymousSessionCookies();
            var setupCookies = merge(anonymousSession, setup(anonymousSession, UUID.randomUUID()));

            // when
            var response = postJson(setupCookies, BASE_PATH + "/auth/social-context", "{\"context\":\"LOGIN\"}");

            // then
            assertThat(response.getStatus().value()).isEqualTo(204);
        }

        @Test
        void localRegistration_verifyEmailThenPasswordReset_invalidatesSetupSession() {
            // given
            var memberId = UUID.randomUUID();
            var anonymousSession = anonymousSessionCookies();
            var setupCookies = merge(anonymousSession, setup(anonymousSession, memberId));
            accountRepository.markEmailVerified(memberId);
            var resetToken = accountTokenService.issuePasswordResetToken(memberId);
            passwordReset.confirmPasswordReset(resetToken, "new correct horse battery staple");

            // when
            var response = get(setupCookies, BASE_PATH + "/me");

            // then
            assertThat(response.getStatus().value()).isEqualTo(401);
        }
    }

    @Nested
    class joinHousehold {

        @Test
        void localRegistration_meReturns200() {
            // given
            var inviteToken = inviteTokenOfNewHousehold();
            var anonymousSession = anonymousSessionCookies();
            var joinCookies = merge(anonymousSession, join(anonymousSession, inviteToken));

            // when
            var response = get(joinCookies, BASE_PATH + "/me");

            // then
            assertThat(response.getStatus().value()).isEqualTo(200);
        }

        @Test
        void localRegistration_oldSessionIdRejected() {
            // given
            var inviteToken = inviteTokenOfNewHousehold();
            var anonymousSession = anonymousSessionCookies();
            var joinCookies = merge(anonymousSession, join(anonymousSession, inviteToken));
            var oldSessionCookies = new LinkedHashMap<>(joinCookies);
            oldSessionCookies.put(SESSION_COOKIE, anonymousSession.get(SESSION_COOKIE));

            // when
            var response = get(oldSessionCookies, BASE_PATH + "/me");

            // then
            assertThat(response.getStatus().value()).isEqualTo(401);
        }
    }

    private UUID inviteTokenOfNewHousehold() {
        var setupResponse = setup(anonymousSessionCookies(), UUID.randomUUID());
        return jsonMapper.readValue(setupResponse.getResponseBody(), HouseholdSetupResponse.class).getInviteToken();
    }

    private EntityExchangeResult<String> setup(Map<String, String> cookies, UUID memberId) {
        var body = """
                {"household": {"id": "%s", "name": "Password Household"},
                 "member": {"id": "%s", "name": "Max Mustermann", "email": "%s"},
                 "localRegistration": {"password": "%s"}}
                """.formatted(UUID.randomUUID(), memberId, uniqueEmail(), RAW_PASSWORD);
        return postJson(cookies, BASE_PATH + "/household/setup", body);
    }

    private EntityExchangeResult<String> join(Map<String, String> cookies, UUID inviteToken) {
        var body = """
                {"id": "%s", "name": "Erika Mustermann", "email": "%s",
                 "localRegistration": {"password": "%s"}}
                """.formatted(UUID.randomUUID(), uniqueEmail(), RAW_PASSWORD);
        return postJson(cookies, BASE_PATH + "/invite/" + inviteToken + "/join", body);
    }

    // Storing a social-login context is the SPA's only way to get a session before registering
    private Map<String, String> anonymousSessionCookies() {
        var cookies = merge(NO_COOKIES, get(NO_COOKIES, BASE_PATH + "/auth/discover"));
        return merge(cookies, postJson(cookies, BASE_PATH + "/auth/social-context", "{\"context\":\"LOGIN\"}"));
    }

    private EntityExchangeResult<String> get(Map<String, String> cookies, String uri) {
        return withCookies(restTestClient.get().uri(uri), cookies)
                .accept(MediaType.APPLICATION_JSON)
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

    private static String uniqueEmail() {
        return "setupflow-" + UUID.randomUUID() + "@example.com";
    }
}
