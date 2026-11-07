package eu.wiegandt.librehousehold.realtime.service;

import eu.wiegandt.librehousehold.realtime.LogCapture;
import eu.wiegandt.librehousehold.realtime.config.RealtimeProperties;
import eu.wiegandt.librehousehold.realtime.exception.StreamLimitExceededProblem;
import eu.wiegandt.librehousehold.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.realtime.model.StreamConnection;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HouseholdEventStreamsTest {

    private static final int LIMIT = 2;
    private static final int UNLIMITED = 100;
    // Distinctive, so a test can tell it apart from any other text in a log line
    private static final String SESSION_ID = "3b9f7c2e-session-id-never-logged";
    private static final String ACTIVE_STREAMS = "librehousehold.realtime.streams.active";
    private static final String CLOSED_STREAMS = "librehousehold.realtime.streams.closed";

    @Nested
    class register {

        @Test
        void newConnection_listedForItsHousehold() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var connection = new StreamConnection(householdId, UUID.randomUUID(), "session", new SseEmitter());

            // when
            streams.register(connection);

            // then
            assertThat(streams.connectionsOf(householdId)).containsExactly(connection);
        }

        @Test
        void secondConnectionOfSameSession_bothListed() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var firstTab = new StreamConnection(householdId, memberId, "session", new SseEmitter());
            var secondTab = new StreamConnection(householdId, memberId, "session", new SseEmitter());
            streams.register(firstTab);

            // when
            streams.register(secondTab);

            // then
            assertThat(streams.connectionsOf(householdId)).containsExactlyInAnyOrder(firstTab, secondTab);
        }

        static Stream<Arguments> limits() {
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var sessionId = "session-" + UUID.randomUUID();
            return Stream.of(
                    argumentSet("per session", limitsOf(LIMIT, UNLIMITED, UNLIMITED),
                            (Supplier<StreamConnection>) () -> new StreamConnection(householdId, memberId, sessionId,
                                    new SseEmitter())),
                    argumentSet("per member", limitsOf(UNLIMITED, LIMIT, UNLIMITED),
                            (Supplier<StreamConnection>) () -> new StreamConnection(householdId, memberId,
                                    "session-" + UUID.randomUUID(), new SseEmitter())),
                    argumentSet("global", limitsOf(UNLIMITED, UNLIMITED, LIMIT),
                            (Supplier<StreamConnection>) () -> new StreamConnection(UUID.randomUUID(),
                                    UUID.randomUUID(), "session-" + UUID.randomUUID(), new SseEmitter())));
        }

        @ParameterizedTest
        @MethodSource("limits")
        void connectionReachingLimit_registered(RealtimeProperties properties,
                                                Supplier<StreamConnection> newConnection) {
            // given
            var streams = new HouseholdEventStreams(properties, new StreamMetrics(new SimpleMeterRegistry()));
            streams.register(newConnection.get());
            var lastAllowed = newConnection.get();

            // when
            streams.register(lastAllowed);

            // then
            assertThat(streams.connections()).contains(lastAllowed);
        }

        @ParameterizedTest
        @MethodSource("limits")
        void connectionBeyondLimit_streamLimitExceededProblem(RealtimeProperties properties,
                                                              Supplier<StreamConnection> newConnection) {
            // given
            var streams = new HouseholdEventStreams(properties, new StreamMetrics(new SimpleMeterRegistry()));
            streams.register(newConnection.get());
            streams.register(newConnection.get());

            // when / then
            assertThatThrownBy(() -> streams.register(newConnection.get()))
                    .isInstanceOf(StreamLimitExceededProblem.class);
        }

        @ParameterizedTest
        @MethodSource("limits")
        void connectionBeyondLimit_notRegistered(RealtimeProperties properties,
                                                 Supplier<StreamConnection> newConnection) {
            // given
            var streams = new HouseholdEventStreams(properties, new StreamMetrics(new SimpleMeterRegistry()));
            streams.register(newConnection.get());
            streams.register(newConnection.get());
            var rejected = newConnection.get();

            // when
            catchThrowable(() -> streams.register(rejected));

            // then
            assertThat(streams.connections()).doesNotContain(rejected);
        }

        @ParameterizedTest
        @MethodSource("limits")
        void closedConnectionAtLimit_newConnectionRegistered(RealtimeProperties properties,
                                                             Supplier<StreamConnection> newConnection) {
            // given
            var streams = new HouseholdEventStreams(properties, new StreamMetrics(new SimpleMeterRegistry()));
            var closed = newConnection.get();
            streams.register(closed);
            streams.register(newConnection.get());
            streams.remove(closed, CloseReason.COMPLETED);
            var replacement = newConnection.get();

            // when
            streams.register(replacement);

            // then
            assertThat(streams.connections()).contains(replacement);
        }

        @Test
        void connectionBeyondLimit_problemCarriesConfiguredRetryAfter() {
            // given
            var properties = new RealtimeProperties(Duration.ofSeconds(5), Duration.ofMinutes(25), 1, UNLIMITED,
                    UNLIMITED, Duration.ofSeconds(42));
            var streams = new HouseholdEventStreams(properties, new StreamMetrics(new SimpleMeterRegistry()));
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            streams.register(new StreamConnection(householdId, memberId, "session", new SseEmitter()));

            // when
            var problem = catchThrowableOfType(StreamLimitExceededProblem.class,
                    () -> streams.register(new StreamConnection(householdId, memberId, "session", new SseEmitter())));

            // then
            assertThat(problem.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
        }

        @Test
        void newConnection_infoWithMemberAndHousehold() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();

            try (var log = LogCapture.of(HouseholdEventStreams.class)) {
                // when
                streams.register(new StreamConnection(householdId, memberId, SESSION_ID, new SseEmitter()));

                // then
                assertThat(log.lines()).containsExactly(
                        "INFO Event stream of member " + memberId + " in household " + householdId + " opened");
            }
        }

        @Test
        void newConnection_activeStreamsGaugeOne() {
            // given
            var registry = new SimpleMeterRegistry();
            var streams = new HouseholdEventStreams(limitsOf(5, 10, 500), new StreamMetrics(registry));

            // when
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), SESSION_ID, new SseEmitter()));

            // then
            assertThat(registry.get(ACTIVE_STREAMS).gauge().value()).isEqualTo(1);
        }

        static Stream<Arguments> rejections() {
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var firstStream = new StreamConnection(householdId, memberId, SESSION_ID, new SseEmitter());
            return Stream.of(
                    argumentSet("per session", limitsOf(1, UNLIMITED, UNLIMITED), firstStream,
                            new StreamConnection(householdId, memberId, SESSION_ID, new SseEmitter()), "per session"),
                    argumentSet("per member", limitsOf(UNLIMITED, 1, UNLIMITED), firstStream,
                            new StreamConnection(householdId, memberId, "session-" + UUID.randomUUID(),
                                    new SseEmitter()), "per member"),
                    argumentSet("global", limitsOf(UNLIMITED, UNLIMITED, 1), firstStream,
                            new StreamConnection(householdId, UUID.randomUUID(), "session-" + UUID.randomUUID(),
                                    new SseEmitter()), "global"));
        }

        @ParameterizedTest
        @MethodSource("rejections")
        void connectionBeyondLimit_warnWithMemberHouseholdAndLimit(RealtimeProperties properties,
                                                                   StreamConnection firstStream,
                                                                   StreamConnection rejected, String limit) {
            // given
            var streams = new HouseholdEventStreams(properties, new StreamMetrics(new SimpleMeterRegistry()));
            streams.register(firstStream);

            try (var log = LogCapture.of(HouseholdEventStreams.class)) {
                // when
                catchThrowable(() -> streams.register(rejected));

                // then
                assertThat(log.lines()).containsExactly("WARN Event stream of member " + rejected.memberId()
                        + " in household " + rejected.householdId() + " rejected: limit " + limit + " reached");
            }
        }
    }

    @Nested
    class remove {

        @Test
        void registeredConnection_noLongerListed() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var connection = new StreamConnection(householdId, UUID.randomUUID(), "session", new SseEmitter());
            streams.register(connection);

            // when
            streams.remove(connection, CloseReason.COMPLETED);

            // then
            assertThat(streams.connectionsOf(householdId)).isEmpty();
        }

        @Test
        void oneOfTwoConnections_otherStaysListed() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var removed = new StreamConnection(householdId, UUID.randomUUID(), "session-a", new SseEmitter());
            var remaining = new StreamConnection(householdId, UUID.randomUUID(), "session-b", new SseEmitter());
            streams.register(removed);
            streams.register(remaining);

            // when
            streams.remove(removed, CloseReason.COMPLETED);

            // then
            assertThat(streams.connectionsOf(householdId)).containsExactly(remaining);
        }

        @Test
        void unknownConnection_registeredConnectionsUnchanged() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var registered = new StreamConnection(householdId, UUID.randomUUID(), "session-a", new SseEmitter());
            streams.register(registered);

            // when
            streams.remove(new StreamConnection(householdId, UUID.randomUUID(), "session-b", new SseEmitter()),
                    CloseReason.COMPLETED);

            // then
            assertThat(streams.connectionsOf(householdId)).containsExactly(registered);
        }

        @Test
        void registeredConnection_infoWithMemberHouseholdAndReason() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var connection = new StreamConnection(householdId, memberId, SESSION_ID, new SseEmitter());
            streams.register(connection);

            try (var log = LogCapture.of(HouseholdEventStreams.class)) {
                // when
                streams.remove(connection, CloseReason.MEMBER_REMOVED);

                // then
                assertThat(log.lines()).containsExactly("INFO Event stream of member " + memberId + " in household "
                        + householdId + " closed: MEMBER_REMOVED");
            }
        }

        @Test
        void openedAndClosedConnection_sessionIdInNoLogLine() {
            // given
            var streams = newStreams();
            var connection = new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), SESSION_ID, new SseEmitter());

            try (var log = LogCapture.of(HouseholdEventStreams.class)) {
                // when
                streams.register(connection);
                streams.remove(connection, CloseReason.COMPLETED);

                // then
                assertThat(log.lines()).isNotEmpty().noneMatch(line -> line.contains(SESSION_ID));
            }
        }

        @Test
        void registeredConnectionRemovedTwice_closedOnceForFirstReason() {
            // given
            var registry = new SimpleMeterRegistry();
            var streams = new HouseholdEventStreams(limitsOf(5, 10, 500), new StreamMetrics(registry));
            var connection = new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), SESSION_ID, new SseEmitter());
            streams.register(connection);

            // when
            streams.remove(connection, CloseReason.TIMEOUT);
            streams.remove(connection, CloseReason.COMPLETED);

            // then
            assertThat(registry.find(CLOSED_STREAMS).counters())
                    .extracting(counter -> counter.getId().getTag("reason"), Counter::count)
                    .containsExactly(tuple("timeout", 1.0));
        }

        @Test
        void registeredConnection_activeStreamsGaugeZero() {
            // given
            var registry = new SimpleMeterRegistry();
            var streams = new HouseholdEventStreams(limitsOf(5, 10, 500), new StreamMetrics(registry));
            var connection = new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), SESSION_ID, new SseEmitter());
            streams.register(connection);

            // when
            streams.remove(connection, CloseReason.COMPLETED);

            // then
            assertThat(registry.get(ACTIVE_STREAMS).gauge().value()).isZero();
        }
    }

    @Nested
    class closeAll {

        @Test
        void openConnections_everyEmitterCompleted() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var firstEmitter = mock(SseEmitter.class);
            var secondEmitter = mock(SseEmitter.class);
            streams.register(new StreamConnection(householdId, UUID.randomUUID(), "session-a", firstEmitter));
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session-b", secondEmitter));

            // when
            streams.closeAll();

            // then
            verify(firstEmitter).complete();
            verify(secondEmitter).complete();
        }

        @Test
        void openConnections_noConnectionListedAnymore() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            streams.register(new StreamConnection(householdId, UUID.randomUUID(), "session", mock(SseEmitter.class)));

            // when
            streams.closeAll();

            // then
            assertThat(streams.connectionsOf(householdId)).isEmpty();
        }
    }

    @Nested
    class connections {

        @Test
        void connectionsOfSeveralHouseholds_allConnections() {
            // given
            var streams = newStreams();
            var firstHousehold = new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session-a", new SseEmitter());
            var secondHousehold = new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session-b", new SseEmitter());
            streams.register(firstHousehold);
            streams.register(secondHousehold);

            // when
            var connections = streams.connections();

            // then
            assertThat(connections).containsExactlyInAnyOrder(firstHousehold, secondHousehold);
        }

        @Test
        void removedConnection_notListed() {
            // given
            var streams = newStreams();
            var connection = new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", new SseEmitter());
            streams.register(connection);
            streams.remove(connection, CloseReason.COMPLETED);

            // when
            var connections = streams.connections();

            // then
            assertThat(connections).isEmpty();
        }
    }

    @Nested
    class connectionsOf {

        @Test
        void householdWithConnections_onlyConnectionsOfThatHousehold() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var ownConnection = new StreamConnection(householdId, UUID.randomUUID(), "session-a", new SseEmitter());
            streams.register(ownConnection);
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session-b", new SseEmitter()));

            // when
            var connections = streams.connectionsOf(householdId);

            // then
            assertThat(connections).containsExactly(ownConnection);
        }

        @Test
        void householdWithoutConnections_empty() {
            // given
            var streams = newStreams();
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", new SseEmitter()));

            // when
            var connections = streams.connectionsOf(UUID.randomUUID());

            // then
            assertThat(connections).isEmpty();
        }
    }

    @Nested
    class connectionsOfMember {

        @Test
        void memberWithConnections_onlyConnectionsOfThatMember() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var ownConnection = new StreamConnection(householdId, memberId, "session-a", new SseEmitter());
            streams.register(ownConnection);
            streams.register(new StreamConnection(householdId, UUID.randomUUID(), "session-b", new SseEmitter()));

            // when
            var connections = streams.connectionsOfMember(memberId);

            // then
            assertThat(connections).containsExactly(ownConnection);
        }

        @Test
        void memberWithoutConnections_empty() {
            // given
            var streams = newStreams();
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", new SseEmitter()));

            // when
            var connections = streams.connectionsOfMember(UUID.randomUUID());

            // then
            assertThat(connections).isEmpty();
        }
    }

    @Nested
    class connectionsOfSession {

        @Test
        void sessionWithConnections_onlyConnectionsOfThatSession() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var ownConnection = new StreamConnection(householdId, memberId, "session-a", new SseEmitter());
            streams.register(ownConnection);
            streams.register(new StreamConnection(householdId, memberId, "session-b", new SseEmitter()));

            // when
            var connections = streams.connectionsOfSession("session-a");

            // then
            assertThat(connections).containsExactly(ownConnection);
        }

        @Test
        void sessionWithoutConnections_empty() {
            // given
            var streams = newStreams();
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session-a", new SseEmitter()));

            // when
            var connections = streams.connectionsOfSession("session-b");

            // then
            assertThat(connections).isEmpty();
        }
    }

    @Nested
    class concurrentAccess {

        private static final int CONNECTION_COUNT = 2_000;
        private static final int THREAD_COUNT = 8;
        private static final int PARALLEL_LIMIT = 200;

        @Test
        void registerAndRemoveInParallel_exactlyTheKeptConnectionsListed() {
            // given
            // No limit may interfere: kept and removed connections share their session IDs
            var properties = new RealtimeProperties(Duration.ofSeconds(5), Duration.ofMinutes(25),
                    2 * CONNECTION_COUNT, 2 * CONNECTION_COUNT, 2 * CONNECTION_COUNT, Duration.ofSeconds(30));
            var streams = new HouseholdEventStreams(properties, new StreamMetrics(new SimpleMeterRegistry()));
            var householdId = UUID.randomUUID();
            var kept = connectionsOf(householdId);
            var removed = connectionsOf(householdId);
            removed.forEach(streams::register);

            // when
            try (var executor = Executors.newFixedThreadPool(THREAD_COUNT)) {
                kept.forEach(connection -> executor.execute(() -> streams.register(connection)));
                removed.forEach(connection -> executor.execute(() -> streams.remove(connection, CloseReason.COMPLETED)));
            }

            // then
            assertThat(streams.connectionsOf(householdId)).containsExactlyInAnyOrderElementsOf(kept);
        }

        @Test
        void registerBeyondLimitInParallel_exactlyLimitRegistered() {
            // given
            var properties = new RealtimeProperties(Duration.ofSeconds(5), Duration.ofMinutes(25), CONNECTION_COUNT,
                    CONNECTION_COUNT, PARALLEL_LIMIT, Duration.ofSeconds(30));
            var streams = new HouseholdEventStreams(properties, new StreamMetrics(new SimpleMeterRegistry()));
            var attempts = connectionsOf(UUID.randomUUID());

            // when
            try (var executor = Executors.newFixedThreadPool(THREAD_COUNT)) {
                attempts.forEach(connection -> executor.execute(() -> catchThrowable(() ->
                        streams.register(connection))));
            }

            // then
            assertThat(streams.connections()).hasSize(PARALLEL_LIMIT);
        }

        private List<StreamConnection> connectionsOf(UUID householdId) {
            return IntStream.range(0, CONNECTION_COUNT)
                    .mapToObj(index -> new StreamConnection(householdId, UUID.randomUUID(), "session-" + index,
                            new SseEmitter()))
                    .toList();
        }
    }

    private static RealtimeProperties limitsOf(int maxStreamsPerSession, int maxStreamsPerMember, int maxStreams) {
        return new RealtimeProperties(Duration.ofSeconds(5), Duration.ofMinutes(25), maxStreamsPerSession,
                maxStreamsPerMember, maxStreams, Duration.ofSeconds(30));
    }

    // The defaults of application.yaml; only the limit tests of HouseholdEventStreamsTest need others
    private HouseholdEventStreams newStreams() {
        return new HouseholdEventStreams(new RealtimeProperties(Duration.ofSeconds(5), Duration.ofMinutes(25), 5, 10,
                500, Duration.ofSeconds(30)), new StreamMetrics(new SimpleMeterRegistry()));
    }
}
