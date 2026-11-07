package eu.wiegandt.librehousehold.realtime.controller;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.household.service.AccountService;
import eu.wiegandt.librehousehold.realtime.SseTestClient;
import eu.wiegandt.librehousehold.realtime.SseTestClient.SseEvent;
import eu.wiegandt.librehousehold.realtime.model.InvalidationSignal;
import eu.wiegandt.librehousehold.realtime.model.SignalEntity;
import eu.wiegandt.librehousehold.realtime.model.SignalType;
import eu.wiegandt.librehousehold.realtime.model.StreamConnection;
import eu.wiegandt.librehousehold.realtime.service.HouseholdEventStreams;
import eu.wiegandt.librehousehold.realtime.service.SignalSender;
import org.apache.catalina.Context;
import org.instancio.Instancio;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.instancio.Select.field;

/**
 * The household event stream over the real server (ADR-019, {@code api/asyncapi.yml}). The stream
 * is read with {@link SseTestClient}; login and cookies work like in
 * {@code MemberSessionInvalidationIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Same properties as FormLoginIT and MemberSessionInvalidationIT, so all share one cached
        // context; the login limits themselves are covered by RateLimitingIT
        "librehousehold.security.rate-limit.login-per-ip.max-attempts=10000",
        "librehousehold.security.rate-limit.login.max-attempts=10000"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
class HouseholdEventStreamIT {

    private static final String BASE_PATH = "/v1";
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";
    private static final Map<String, String> NO_COOKIES = Map.of();
    private static final String SESSION_COOKIE = "JSESSIONID";
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);
    // How long the log is watched after the stream ended; the container logs a failed dispatch right away
    private static final Duration LOG_QUIET_PERIOD = Duration.ofSeconds(1);
    private static final String COMMITTED_RESPONSE_FAILURE = "response is already committed";
    private static final String ACCESS_DENIED_FAILURE = "AccessDeniedException";
    private static final String AUTHORIZATION_DENIED_FAILURE = "AuthorizationDeniedException";

    @LocalServerPort
    private int port;

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
    private HouseholdEventStreams streams;

    @Autowired
    private SignalSender signalSender;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private WebServerApplicationContext webServerContext;

    @Test
    void openStream_member_streamingResponseHead() throws Exception {
        // given
        var member = createVerifiedMember(createHousehold());
        var expected = new ResponseHead(200, MediaType.TEXT_EVENT_STREAM_VALUE, "no-store", "no");

        // when
        try (var stream = SseTestClient.open(streamUri(member.householdId()), loggedInCookies(member))) {

            // then
            assertThat(ResponseHead.of(stream)).isEqualTo(expected);
        }
    }

    @Test
    void openStream_member_firstBlockSetsReconnectDelay() throws Exception {
        // given
        var member = createVerifiedMember(createHousehold());
        var expected = new SseEvent(null, null, 5000L, List.of());

        // when
        try (var stream = SseTestClient.open(streamUri(member.householdId()), loggedInCookies(member))) {

            // then
            assertThat(stream.awaitEvent(EVENT_TIMEOUT)).isEqualTo(expected);
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class authorization {

        @Test
        void openStream_withoutSession_unauthorizedWithoutRedirectAndStream() throws Exception {
            // given
            var householdId = createHousehold();
            var expected = new Rejection(401, null, 0);

            // when
            try (var stream = SseTestClient.open(streamUri(householdId), NO_COOKIES)) {

                // then
                assertThat(Rejection.of(stream, streams.connectionsOf(householdId).size())).isEqualTo(expected);
            }
        }

        @Test
        void openStream_foreignHousehold_forbiddenWithoutStream() throws Exception {
            // given
            var foreignHouseholdId = createHousehold();
            var member = createVerifiedMember(createHousehold());
            var expected = new Rejection(403, null, 0);

            // when
            try (var stream = SseTestClient.open(streamUri(foreignHouseholdId), loggedInCookies(member))) {

                // then
                assertThat(Rejection.of(stream, streams.connectionsOf(foreignHouseholdId).size())).isEqualTo(expected);
            }
        }

        @Test
        void openStream_expiredSession_unauthorizedWithoutRedirectAndStream() throws Exception {
            // given
            var member = createVerifiedMember(createHousehold());
            var cookies = loggedInCookies(member);
            sessionRegistry.getSessionInformation(cookies.get(SESSION_COOKIE)).expireNow();
            var expected = new Rejection(401, null, 0);

            // when
            try (var stream = SseTestClient.open(streamUri(member.householdId()), cookies)) {

                // then
                assertThat(Rejection.of(stream, streams.connectionsOf(member.householdId()).size()))
                        .isEqualTo(expected);
            }
        }

        @Test
        void openStream_logoutThenServerEndsStream_streamClosed() throws Exception {
            // given
            var member = createVerifiedMember(createHousehold());
            var cookies = loggedInCookies(member);

            try (var stream = SseTestClient.open(streamUri(member.householdId()), cookies)) {
                stream.awaitEvent(EVENT_TIMEOUT);
                logout(cookies);

                // when
                endStreams(member.householdId());

                // then
                assertThat(stream.awaitClosed(CLOSE_TIMEOUT)).isTrue();
            }
        }

        @Test
        void openStream_logoutThenServerEndsStream_noAuthorizationFailureLogged(CapturedOutput output) throws Exception {
            // given
            var member = createVerifiedMember(createHousehold());
            var cookies = loggedInCookies(member);

            try (var stream = SseTestClient.open(streamUri(member.householdId()), cookies)) {
                stream.awaitEvent(EVENT_TIMEOUT);
                logout(cookies);

                // when
                endStreams(member.householdId());
                stream.awaitClosed(CLOSE_TIMEOUT);

                // then
                await().during(LOG_QUIET_PERIOD).atMost(LOG_QUIET_PERIOD.multipliedBy(2))
                        .untilAsserted(() -> assertThat(output.getAll())
                                .doesNotContain(COMMITTED_RESPONSE_FAILURE, ACCESS_DENIED_FAILURE,
                                        AUTHORIZATION_DENIED_FAILURE));
            }
        }

        @Test
        void openStream_signalAfterFirstBlock_signalReceived() throws Exception {
            // given
            var member = createVerifiedMember(createHousehold());
            var taskId = UUID.fromString("7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f");
            var occurredAt = "2026-11-07T18:30:00Z";
            var signal = new InvalidationSignal(SignalEntity.TASK, taskId, Instant.parse(occurredAt));
            var expected = new SseEvent(SignalType.TASK_CREATED.wireName(),
                    "{\"entity\":\"task\",\"entityId\":\"" + taskId + "\",\"occurredAt\":\"" + occurredAt + "\"}",
                    null, List.of());

            try (var stream = SseTestClient.open(streamUri(member.householdId()), loggedInCookies(member))) {
                stream.awaitEvent(EVENT_TIMEOUT);

                // when
                signalSender.send(SignalType.TASK_CREATED, signal, streams.connectionsOf(member.householdId()));

                // then
                assertThat(stream.awaitEvent(EVENT_TIMEOUT)).isEqualTo(expected);
            }
        }

        @Test
        void openStream_member_sessionAttributesUnchanged() throws Exception {
            // given
            var member = createVerifiedMember(createHousehold());
            var cookies = loggedInCookies(member);
            var sessionId = cookies.get(SESSION_COOKIE);
            var attributesBefore = sessionAttributes(sessionId);

            // when
            try (var stream = SseTestClient.open(streamUri(member.householdId()), cookies)) {
                stream.awaitEvent(EVENT_TIMEOUT);
                endStreams(member.householdId());
                stream.awaitClosed(CLOSE_TIMEOUT);
            }

            // then
            assertThat(sessionAttributes(sessionId)).isEqualTo(attributesBefore);
        }
    }

    private record Rejection(int status, @Nullable String location, int openStreams) {

        static Rejection of(SseTestClient stream, int openStreams) {
            return new Rejection(stream.status(), stream.headers().firstValue("Location").orElse(null), openStreams);
        }
    }

    private record ResponseHead(int status, @Nullable String contentType, @Nullable String cacheControl,
                                @Nullable String accelBuffering) {

        static ResponseHead of(SseTestClient stream) {
            var headers = stream.headers();
            return new ResponseHead(stream.status(),
                    headers.firstValue("Content-Type").orElse(null),
                    headers.firstValue("Cache-Control").orElse(null),
                    headers.firstValue("X-Accel-Buffering").orElse(null));
        }
    }

    private URI streamUri(UUID householdId) {
        return URI.create("http://localhost:" + port + BASE_PATH + "/household/" + householdId + "/events");
    }

    // Completes the streams on the server side; each response then ends with an ASYNC dispatch
    private void endStreams(UUID householdId) {
        streams.connectionsOf(householdId).stream().map(StreamConnection::emitter).forEach(SseEmitter::complete);
    }

    private void logout(Map<String, String> cookies) {
        withCookies(restTestClient.post().uri(BASE_PATH + "/logout"), cookies)
                .header(XSRF_HEADER, cookies.get(XSRF_COOKIE))
                .exchange()
                .returnResult(String.class);
    }

    // Reads the container's session directly, so the comparison sees every attribute, not only
    // those an endpoint exposes
    private Map<String, Object> sessionAttributes(String sessionId) throws IOException {
        var tomcat = ((TomcatWebServer) webServerContext.getWebServer()).getTomcat();
        var context = (Context) tomcat.getHost().findChildren()[0];
        var session = context.getManager().findSession(sessionId).getSession();
        return Collections.list(session.getAttributeNames()).stream()
                .collect(Collectors.toMap(Function.identity(), session::getAttribute));
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
