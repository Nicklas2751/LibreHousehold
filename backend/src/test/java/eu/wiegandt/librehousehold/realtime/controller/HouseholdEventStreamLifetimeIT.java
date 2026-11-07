package eu.wiegandt.librehousehold.realtime.controller;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.household.service.AccountService;
import eu.wiegandt.librehousehold.household.service.MemberSessions;
import eu.wiegandt.librehousehold.realtime.SseTestClient;
import eu.wiegandt.librehousehold.realtime.SseTestClient.SseEvent;
import eu.wiegandt.librehousehold.realtime.service.HouseholdEventStreams;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.instancio.Select.field;

/**
 * How long a stream lives: until its emitter timeout, or until the heartbeat finds its session ended
 * (ADR-019, RT3). A separate class, since the short timeout and heartbeat interval need their own
 * application context; the other stream tests keep the defaults.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Long enough to tell a stream ended by the heartbeat from one ended by the timeout
        "librehousehold.realtime.emitter-timeout=3s",
        "librehousehold.realtime.heartbeat-interval=200ms",
        // The login limits themselves are covered by RateLimitingIT
        "librehousehold.security.rate-limit.login-per-ip.max-attempts=10000",
        "librehousehold.security.rate-limit.login.max-attempts=10000"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@ExtendWith(OutputCaptureExtension.class)
class HouseholdEventStreamLifetimeIT {

    private static final String BASE_PATH = "/v1";
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";
    private static final Map<String, String> NO_COOKIES = Map.of();
    // Well above the emitter timeout of 3 seconds
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(2);
    // A few heartbeat intervals, but well below the emitter timeout
    private static final Duration HEARTBEAT_CLOSE_TIMEOUT = Duration.ofSeconds(1);
    // How long the log is watched after the stream ended; the container logs a failed dispatch right away
    private static final Duration LOG_QUIET_PERIOD = Duration.ofSeconds(1);
    private static final SseEvent HEARTBEAT = new SseEvent(null, null, null, List.of("heartbeat"));
    private static final String COMMITTED_RESPONSE_FAILURE = "response is already committed";
    private static final String ACCESS_DENIED_FAILURE = "AccessDeniedException";
    private static final String AUTHORIZATION_DENIED_FAILURE = "AuthorizationDeniedException";
    private static final String DISPATCH_FAILURE = "Servlet.service()";

    @LocalServerPort
    private int port;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private HouseholdEventStreams streams;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private MemberSessions memberSessions;

    @Test
    void openStream_emitterTimeoutElapses_connectionRemovedFromRegistry() throws Exception {
        // given
        var householdId = createHousehold();
        var member = createVerifiedMember(householdId);

        // when
        try (var _ = SseTestClient.open(streamUri(householdId), loggedInCookies(member))) {

            // then
            await().atMost(CLOSE_TIMEOUT)
                    .untilAsserted(() -> assertThat(streams.connectionsOf(householdId)).isEmpty());
        }
    }

    @Test
    void openStream_member_heartbeatCommentsReceived() throws Exception {
        // given
        var member = createVerifiedMember(createHousehold());

        // when
        try (var stream = SseTestClient.open(streamUri(member.householdId()), loggedInCookies(member))) {
            stream.awaitEvent(EVENT_TIMEOUT);

            // then
            assertThat(List.of(stream.awaitEvent(EVENT_TIMEOUT), stream.awaitEvent(EVENT_TIMEOUT)))
                    .containsExactly(HEARTBEAT, HEARTBEAT);
        }
    }

    @Test
    void openStream_sessionsOfMemberExpired_streamClosedByNextHeartbeat() throws Exception {
        // given
        var member = createVerifiedMember(createHousehold());

        try (var stream = SseTestClient.open(streamUri(member.householdId()), loggedInCookies(member))) {
            stream.awaitEvent(EVENT_TIMEOUT);

            // when
            memberSessions.expireAll(member.getId());

            // then
            assertThat(stream.awaitClosed(HEARTBEAT_CLOSE_TIMEOUT)).isTrue();
        }
    }

    @Test
    void openStream_sessionsOfMemberExpired_noDispatchFailureLogged(CapturedOutput output) throws Exception {
        // given
        var member = createVerifiedMember(createHousehold());

        try (var stream = SseTestClient.open(streamUri(member.householdId()), loggedInCookies(member))) {
            stream.awaitEvent(EVENT_TIMEOUT);

            // when
            memberSessions.expireAll(member.getId());
            stream.awaitClosed(HEARTBEAT_CLOSE_TIMEOUT);

            // then
            await().during(LOG_QUIET_PERIOD).atMost(LOG_QUIET_PERIOD.multipliedBy(2))
                    .untilAsserted(() -> assertThat(output.getAll())
                            .doesNotContain(COMMITTED_RESPONSE_FAILURE, ACCESS_DENIED_FAILURE,
                                    AUTHORIZATION_DENIED_FAILURE, DISPATCH_FAILURE));
        }
    }

    private URI streamUri(UUID householdId) {
        return URI.create("http://localhost:" + port + BASE_PATH + "/household/" + householdId + "/events");
    }

    private UUID createHousehold() {
        return householdRepository.save(Instancio.create(HouseholdEntity.class)).id();
    }

    private MemberEntity createVerifiedMember(UUID householdId) {
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), householdId)
                .set(field(MemberEntity::email), "event-stream-" + UUID.randomUUID() + "@example.com")
                .set(field(MemberEntity::isAdmin), false)
                .create());
        accountService.createAccount(member.getId(), RAW_PASSWORD);
        accountRepository.markEmailVerified(member.getId());
        return member;
    }

    // Every login issues a new XSRF-TOKEN, which the merge keeps for follow-up requests
    private Map<String, String> loggedInCookies(MemberEntity member) {
        var cookies = merge(NO_COOKIES, withCookies(restTestClient.get().uri(BASE_PATH + "/auth/discover"), NO_COOKIES)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .returnResult(String.class));
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
}
