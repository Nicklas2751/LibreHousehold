package eu.wiegandt.librehousehold.realtime.controller;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.household.service.AccountService;
import eu.wiegandt.librehousehold.realtime.AsyncApiContract;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final String DISPATCH_FAILURE = "Servlet.service()";
    private static final String WARN_LEVEL = " WARN ";
    private static final String ERROR_LEVEL = " ERROR ";
    private static final String STACK_TRACE_LINE = "\tat ";
    // How long a stream that should stay open is watched; well below the 25-second heartbeat interval
    private static final Duration OPEN_STREAM_OBSERVATION = Duration.ofSeconds(1);
    private static final String FIND_LISTENER_IDS_OF_EVENT_SQL =
            "SELECT listener_id FROM event_publication WHERE serialized_event LIKE ?";
    private static final String NOTIFICATIONS_LISTENER = "HouseholdDeletedListener";
    private static final String REALTIME_LISTENER = "StreamLifecycleListener";
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

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
        void openStream_foreignHousehold_denialLoggedAsWarning(CapturedOutput output) throws Exception {
            // given
            var foreignHouseholdId = createHousehold();
            var member = createVerifiedMember(createHousehold());
            var denial = "Event stream of member " + member.getId() + " in household " + foreignHouseholdId
                    + " denied: 403";

            // when
            try (var _ = SseTestClient.open(streamUri(foreignHouseholdId), loggedInCookies(member))) {

                // then
                assertThat(output.getAll().lines()).anyMatch(line -> line.contains(WARN_LEVEL) && line.contains(denial));
            }
        }

        @Test
        void getTasks_foreignHousehold_noStreamDenialLogged(CapturedOutput output) {
            // given
            var foreignHouseholdId = createHousehold();
            var cookies = loggedInCookies(createVerifiedMember(createHousehold()));

            // when
            withCookies(restTestClient.get().uri(BASE_PATH + "/household/" + foreignHouseholdId + "/tasks"), cookies)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .returnResult(String.class);

            // then
            assertThat(output.getAll()).doesNotContain("in household " + foreignHouseholdId + " denied");
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
        void openStream_logout_streamClosed() throws Exception {
            // given
            var member = createVerifiedMember(createHousehold());
            var cookies = loggedInCookies(member);

            try (var stream = SseTestClient.open(streamUri(member.householdId()), cookies)) {
                stream.awaitEvent(EVENT_TIMEOUT);

                // when
                logout(cookies);

                // then
                assertThat(stream.awaitClosed(CLOSE_TIMEOUT)).isTrue();
            }
        }

        @Test
        void openStream_logout_noAuthorizationFailureLogged(CapturedOutput output) throws Exception {
            // given
            var member = createVerifiedMember(createHousehold());
            var cookies = loggedInCookies(member);

            try (var stream = SseTestClient.open(streamUri(member.householdId()), cookies)) {
                stream.awaitEvent(EVENT_TIMEOUT);

                // when
                logout(cookies);
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

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class lifecycle {

        @Test
        void removeMember_streamOfRemovedMember_sessionTerminatedThenClosed() throws Exception {
            // given
            var householdId = createHousehold();
            var adminCookies = loggedInCookies(createVerifiedMember(householdId, true));
            var member = createVerifiedMember(householdId, false);
            var expected = new Ending(List.of(SignalType.SESSION_TERMINATED.wireName()), true);

            try (var stream = SseTestClient.open(streamUri(householdId), loggedInCookies(member))) {
                stream.awaitEvent(EVENT_TIMEOUT);

                // when
                delete(adminCookies, memberUri(member));

                // then
                assertThat(awaitEnding(stream)).isEqualTo(expected);
            }
        }

        @Test
        void removeMember_streamOfOtherMember_staysOpen() throws Exception {
            // given
            var householdId = createHousehold();
            var adminCookies = loggedInCookies(createVerifiedMember(householdId, true));
            var member = createVerifiedMember(householdId, false);

            try (var adminStream = SseTestClient.open(streamUri(householdId), adminCookies);
                 var memberStream = SseTestClient.open(streamUri(householdId), loggedInCookies(member))) {
                adminStream.awaitEvent(EVENT_TIMEOUT);
                memberStream.awaitEvent(EVENT_TIMEOUT);

                // when
                delete(adminCookies, memberUri(member));
                memberStream.awaitClosed(CLOSE_TIMEOUT);

                // then
                assertThat(adminStream.awaitClosed(OPEN_STREAM_OBSERVATION)).isFalse();
            }
        }

        @Test
        void removeMember_streamOfRemovedMember_noDispatchFailureLogged(CapturedOutput output) throws Exception {
            // given
            var householdId = createHousehold();
            var adminCookies = loggedInCookies(createVerifiedMember(householdId, true));
            var member = createVerifiedMember(householdId, false);

            try (var stream = SseTestClient.open(streamUri(householdId), loggedInCookies(member))) {
                stream.awaitEvent(EVENT_TIMEOUT);

                // when
                delete(adminCookies, memberUri(member));
                stream.awaitClosed(CLOSE_TIMEOUT);

                // then
                await().during(LOG_QUIET_PERIOD).atMost(LOG_QUIET_PERIOD.multipliedBy(2))
                        .untilAsserted(() -> assertThat(output.getAll())
                                .doesNotContain(COMMITTED_RESPONSE_FAILURE, ACCESS_DENIED_FAILURE,
                                        AUTHORIZATION_DENIED_FAILURE, DISPATCH_FAILURE));
            }
        }

        @Test
        void deleteHousehold_streamsOfAllMembers_householdDeletedThenSessionTerminatedThenClosed() throws Exception {
            // given
            var householdId = createHousehold();
            var adminCookies = loggedInCookies(createVerifiedMember(householdId, true));
            var memberCookies = loggedInCookies(createVerifiedMember(householdId, false));
            var expected = new Ending(List.of(SignalType.HOUSEHOLD_DELETED.wireName(),
                    SignalType.SESSION_TERMINATED.wireName()), true);

            try (var adminStream = SseTestClient.open(streamUri(householdId), adminCookies);
                 var memberStream = SseTestClient.open(streamUri(householdId), memberCookies)) {
                adminStream.awaitEvent(EVENT_TIMEOUT);
                memberStream.awaitEvent(EVENT_TIMEOUT);

                // when
                delete(adminCookies, BASE_PATH + "/household/" + householdId);

                // then
                assertThat(List.of(awaitEnding(adminStream), awaitEnding(memberStream)))
                        .containsExactly(expected, expected);
            }
        }

        @Test
        void deleteHousehold_otherClientDisconnected_householdDeletedThenSessionTerminatedThenClosed()
                throws Exception {
            // given
            var householdId = createHousehold();
            var adminCookies = loggedInCookies(createVerifiedMember(householdId, true));
            var memberCookies = loggedInCookies(createVerifiedMember(householdId, false));
            var expected = new Ending(List.of(SignalType.HOUSEHOLD_DELETED.wireName(),
                    SignalType.SESSION_TERMINATED.wireName()), true);

            try (var adminStream = SseTestClient.open(streamUri(householdId), adminCookies)) {
                adminStream.awaitEvent(EVENT_TIMEOUT);
                disconnect(SseTestClient.open(streamUri(householdId), memberCookies));

                // when
                delete(adminCookies, BASE_PATH + "/household/" + householdId);

                // then
                assertThat(awaitEnding(adminStream)).isEqualTo(expected);
            }
        }

        @Test
        void deleteHousehold_otherClientDisconnected_noErrorLogged(CapturedOutput output) throws Exception {
            // given
            var householdId = createHousehold();
            var adminCookies = loggedInCookies(createVerifiedMember(householdId, true));
            var memberCookies = loggedInCookies(createVerifiedMember(householdId, false));

            try (var adminStream = SseTestClient.open(streamUri(householdId), adminCookies)) {
                adminStream.awaitEvent(EVENT_TIMEOUT);
                disconnect(SseTestClient.open(streamUri(householdId), memberCookies));

                // when
                delete(adminCookies, BASE_PATH + "/household/" + householdId);
                adminStream.awaitClosed(CLOSE_TIMEOUT);

                // then
                await().during(LOG_QUIET_PERIOD).atMost(LOG_QUIET_PERIOD.multipliedBy(2))
                        .untilAsserted(() -> assertThat(output.getAll())
                                .doesNotContain(ERROR_LEVEL, STACK_TRACE_LINE, DISPATCH_FAILURE));
            }
        }

        @Test
        void deleteHousehold_openStream_publicationRecordedOnlyForRegistryListeners() throws Exception {
            // given
            var householdId = createHousehold();
            var adminCookies = loggedInCookies(createVerifiedMember(householdId, true));

            try (var stream = SseTestClient.open(streamUri(householdId), adminCookies)) {
                stream.awaitEvent(EVENT_TIMEOUT);

                // when
                delete(adminCookies, BASE_PATH + "/household/" + householdId);
                stream.awaitClosed(CLOSE_TIMEOUT);

                // then
                assertThat(jdbcTemplate.queryForList(FIND_LISTENER_IDS_OF_EVENT_SQL, String.class,
                        "%" + householdId + "%"))
                        .anyMatch(listenerId -> listenerId.contains(NOTIFICATIONS_LISTENER))
                        .noneMatch(listenerId -> listenerId.contains(REALTIME_LISTENER));
            }
        }
    }

    /**
     * The bytes on the wire against {@code api/asyncapi.yml}: every {@code data:} is one line of JSON
     * (RT6) with exactly the fields of the payload schema its message names (RT11). Covers the events
     * R1 sends on its own and a change signal sent through the {@link SignalSender}.
     */
    @Nested
    class contract {

        @Test
        void openStream_invalidationSignalSent_dataIsOneJsonLineWithContractFields() throws Exception {
            // given
            var member = createVerifiedMember(createHousehold());
            var signal = new InvalidationSignal(SignalEntity.TASK,
                    UUID.fromString("7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f"), Instant.parse("2026-11-07T18:30:00Z"));
            var expected = ContractConformance.conforming(SignalType.TASK_CREATED.wireName());

            try (var stream = SseTestClient.open(streamUri(member.householdId()), loggedInCookies(member))) {
                stream.awaitEvent(EVENT_TIMEOUT);

                // when
                signalSender.send(SignalType.TASK_CREATED, signal, streams.connectionsOf(member.householdId()));

                // then
                assertThat(conformanceOf(stream.awaitEvent(EVENT_TIMEOUT))).isEqualTo(expected);
            }
        }

        @Test
        void openStream_householdDeleted_everyDataIsOneJsonLineWithContractFields() throws Exception {
            // given
            var householdId = createHousehold();
            var adminCookies = loggedInCookies(createVerifiedMember(householdId, true));
            var expected = List.of(ContractConformance.conforming(SignalType.HOUSEHOLD_DELETED.wireName()),
                    ContractConformance.conforming(SignalType.SESSION_TERMINATED.wireName()));

            try (var stream = SseTestClient.open(streamUri(householdId), adminCookies)) {
                stream.awaitEvent(EVENT_TIMEOUT);

                // when
                delete(adminCookies, BASE_PATH + "/household/" + householdId);
                stream.awaitClosed(CLOSE_TIMEOUT);

                // then
                assertThat(stream.takeReceivedEvents().stream().map(HouseholdEventStreamIT.this::conformanceOf))
                        .containsExactlyElementsOf(expected);
            }
        }
    }

    /**
     * How one received event matches its message in {@code api/asyncapi.yml}.
     *
     * @param dataLines        the number of {@code data:} lines of the block
     * @param missingFields    the {@code required} fields the payload lacks
     * @param undeclaredFields the payload fields the schema does not allow
     */
    private record ContractConformance(@Nullable String eventName, long dataLines, Set<String> missingFields,
                                       Set<String> undeclaredFields) {

        static ContractConformance conforming(String eventName) {
            return new ContractConformance(eventName, 1, Set.of(), Set.of());
        }
    }

    /**
     * How a stream ended: the names of the events it received before, and whether the server ended it.
     */
    private record Ending(List<@Nullable String> eventNames, boolean closed) {
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

    // Waits for the first block, so the stream is registered, then drops the connection like a closed
    // browser tab. The server only notices on its next write, so the connection is still registered when
    // a broadcast runs, and writing to it fails with a broken pipe (RT12)
    private void disconnect(SseTestClient stream) throws InterruptedException {
        try (stream) {
            stream.awaitEvent(EVENT_TIMEOUT);
        }
    }

    private Ending awaitEnding(SseTestClient stream) throws InterruptedException {
        var closed = stream.awaitClosed(CLOSE_TIMEOUT);
        return new Ending(stream.takeReceivedEvents().stream().map(SseEvent::name).toList(), closed);
    }

    // The parser joins several data: lines with a line feed, so counting lines recovers them
    private ContractConformance conformanceOf(SseEvent event) {
        var schema = AsyncApiContract.load().payloadOf(event.name());
        var fields = jsonMapper.readValue(event.data(), JSON_OBJECT).keySet();
        var undeclaredFields = schema.additionalProperties()
                ? Set.<String>of()
                : difference(fields, schema.properties());
        return new ContractConformance(event.name(), event.data().lines().count(),
                difference(schema.required(), fields), undeclaredFields);
    }

    private static Set<String> difference(Set<String> fields, Set<String> excluded) {
        return fields.stream()
                .filter(fieldName -> !excluded.contains(fieldName))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String memberUri(MemberEntity member) {
        return BASE_PATH + "/household/" + member.householdId() + "/members/" + member.getId();
    }

    private void delete(Map<String, String> cookies, String uri) {
        withCookies(restTestClient.delete().uri(uri), cookies)
                .header(XSRF_HEADER, cookies.get(XSRF_COOKIE))
                .exchange()
                .expectStatus().isNoContent();
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
        return createVerifiedMember(householdId, false);
    }

    private MemberEntity createVerifiedMember(UUID householdId, boolean isAdmin) {
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), householdId)
                .set(field(MemberEntity::email), "event-stream-" + UUID.randomUUID() + "@example.com")
                .set(field(MemberEntity::isAdmin), isAdmin)
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
