package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.household.service.AccountService;
import eu.wiegandt.librehousehold.household.service.UnverifiedAccountExpiryJob;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

/**
 * Proves that a member who leaves or loses the household cannot keep using a session that was
 * established before (Arc42 Chapter 8): the next request with that session is rejected. Runs the
 * real server and logs in like the SPA does (see {@link FormLoginIT}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Same properties as FormLoginIT, so both share one cached context; the per-IP login limit
        // is covered by RateLimitingIT
        "librehousehold.security.rate-limit.login-per-ip.max-attempts=10000",
        "librehousehold.security.rate-limit.login.max-attempts=10000"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
class MemberSessionInvalidationIT {

    private static final String BASE_PATH = "/v1";
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";
    private static final Map<String, String> NO_COOKIES = Map.of();
    private static final Duration PAST_VERIFICATION_GRACE_PERIOD = Duration.ofDays(8);
    private static final String BACKDATE_REGISTRATION_SQL = "UPDATE account SET registered_at = ? WHERE member_id = ?";

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
    private UnverifiedAccountExpiryJob unverifiedAccountExpiryJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void leaveHousehold_existingSession_rejected() {
        // given
        var member = createVerifiedMember(createHousehold(), false);
        var sessionCookies = loggedInCookies(member);
        delete(sessionCookies, memberUri(member) + "/account");

        // when
        var response = get(sessionCookies, BASE_PATH + "/me");

        // then
        assertThat(response.getStatus().value()).isEqualTo(401);
    }

    @Test
    void removeMember_existingSession_rejected() {
        // given
        var householdId = createHousehold();
        var admin = createVerifiedMember(householdId, true);
        var member = createVerifiedMember(householdId, false);
        var memberCookies = loggedInCookies(member);
        delete(loggedInCookies(admin), memberUri(member));

        // when
        var response = get(memberCookies, BASE_PATH + "/me");

        // then
        assertThat(response.getStatus().value()).isEqualTo(401);
    }

    @Test
    void deleteHousehold_existingSessions_rejected() {
        // given
        var householdId = createHousehold();
        var adminCookies = loggedInCookies(createVerifiedMember(householdId, true));
        var memberCookies = loggedInCookies(createVerifiedMember(householdId, false));
        delete(adminCookies, BASE_PATH + "/household/" + householdId);

        // when
        var adminResponse = get(adminCookies, BASE_PATH + "/me");
        var memberResponse = get(memberCookies, BASE_PATH + "/me");

        // then
        assertThat(List.of(adminResponse.getStatus().value(), memberResponse.getStatus().value())).containsOnly(401);
    }

    @Test
    void unverifiedAccountExpiry_existingSession_rejected() {
        // given — an unverified account cannot log in, so the session comes from the household setup
        var memberId = UUID.randomUUID();
        var cookies = xsrfCookies();
        var sessionCookies = merge(cookies, setup(cookies, memberId));
        jdbcTemplate.update(BACKDATE_REGISTRATION_SQL,
                Timestamp.from(Instant.now().minus(PAST_VERIFICATION_GRACE_PERIOD)), memberId);
        unverifiedAccountExpiryJob.run();

        // when
        var response = get(sessionCookies, BASE_PATH + "/me");

        // then
        assertThat(response.getStatus().value()).isEqualTo(401);
    }

    @Test
    void removeHouseholdAdmin_returns4xx() {
        // given
        var admin = createVerifiedMember(createHousehold(), true);

        // when
        var response = delete(loggedInCookies(admin), memberUri(admin));

        // then
        assertThat(response.getStatus().is4xxClientError()).isTrue();
    }

    @Test
    void removeHouseholdAdmin_existingSessionStaysValid() {
        // given
        var admin = createVerifiedMember(createHousehold(), true);
        var adminCookies = loggedInCookies(admin);
        delete(adminCookies, memberUri(admin));

        // when
        var response = get(adminCookies, BASE_PATH + "/me");

        // then
        assertThat(response.getStatus().value()).isEqualTo(200);
    }

    private UUID createHousehold() {
        return householdRepository.save(Instancio.create(HouseholdEntity.class)).id();
    }

    private MemberEntity createVerifiedMember(UUID householdId, boolean isAdmin) {
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), householdId)
                .set(field(MemberEntity::email), uniqueEmail())
                .set(field(MemberEntity::isAdmin), isAdmin)
                .create());
        accountService.createAccount(member.getId(), RAW_PASSWORD);
        accountRepository.markEmailVerified(member.getId());
        return member;
    }

    private static String memberUri(MemberEntity member) {
        return BASE_PATH + "/household/" + member.householdId() + "/members/" + member.getId();
    }

    // Every login issues a new XSRF-TOKEN, which the merge keeps for follow-up requests
    private Map<String, String> loggedInCookies(MemberEntity member) {
        var cookies = xsrfCookies();
        var form = new LinkedMultiValueMap<String, String>();
        form.add("email", member.email());
        form.add("password", RAW_PASSWORD);
        var loginResponse = withCookies(restTestClient.post().uri(BASE_PATH + "/login"), cookies)
                .header(XSRF_HEADER, cookies.get(XSRF_COOKIE))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange()
                .returnResult(String.class);
        return merge(cookies, loginResponse);
    }

    private Map<String, String> xsrfCookies() {
        return merge(NO_COOKIES, get(NO_COOKIES, BASE_PATH + "/auth/discover"));
    }

    private EntityExchangeResult<String> setup(Map<String, String> cookies, UUID memberId) {
        var body = """
                {"household": {"id": "%s", "name": "Expiring Household"},
                 "member": {"id": "%s", "name": "Max Mustermann", "email": "%s"},
                 "localRegistration": {"password": "%s"}}
                """.formatted(UUID.randomUUID(), memberId, uniqueEmail(), RAW_PASSWORD);
        return withCookies(restTestClient.post().uri(BASE_PATH + "/household/setup"), cookies)
                .header(XSRF_HEADER, cookies.get(XSRF_COOKIE))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .returnResult(String.class);
    }

    private EntityExchangeResult<String> get(Map<String, String> cookies, String uri) {
        return withCookies(restTestClient.get().uri(uri), cookies)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .returnResult(String.class);
    }

    private EntityExchangeResult<String> delete(Map<String, String> cookies, String uri) {
        return withCookies(restTestClient.delete().uri(uri), cookies)
                .header(XSRF_HEADER, cookies.get(XSRF_COOKIE))
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
        return "session-invalidation-" + UUID.randomUUID() + "@example.com";
    }
}
