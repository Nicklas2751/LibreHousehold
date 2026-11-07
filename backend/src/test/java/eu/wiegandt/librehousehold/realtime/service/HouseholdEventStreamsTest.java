package eu.wiegandt.librehousehold.realtime.service;

import eu.wiegandt.librehousehold.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.realtime.model.StreamConnection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HouseholdEventStreamsTest {

    @Nested
    class register {

        @Test
        void newConnection_listedForItsHousehold() {
            // given
            var streams = new HouseholdEventStreams();
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
            var streams = new HouseholdEventStreams();
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
    }

    @Nested
    class remove {

        @Test
        void registeredConnection_noLongerListed() {
            // given
            var streams = new HouseholdEventStreams();
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
            var streams = new HouseholdEventStreams();
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
            var streams = new HouseholdEventStreams();
            var householdId = UUID.randomUUID();
            var registered = new StreamConnection(householdId, UUID.randomUUID(), "session-a", new SseEmitter());
            streams.register(registered);

            // when
            streams.remove(new StreamConnection(householdId, UUID.randomUUID(), "session-b", new SseEmitter()),
                    CloseReason.COMPLETED);

            // then
            assertThat(streams.connectionsOf(householdId)).containsExactly(registered);
        }
    }

    @Nested
    class closeAll {

        @Test
        void openConnections_everyEmitterCompleted() {
            // given
            var streams = new HouseholdEventStreams();
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
            var streams = new HouseholdEventStreams();
            var householdId = UUID.randomUUID();
            streams.register(new StreamConnection(householdId, UUID.randomUUID(), "session", mock(SseEmitter.class)));

            // when
            streams.closeAll();

            // then
            assertThat(streams.connectionsOf(householdId)).isEmpty();
        }
    }

    @Nested
    class connectionsOf {

        @Test
        void householdWithConnections_onlyConnectionsOfThatHousehold() {
            // given
            var streams = new HouseholdEventStreams();
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
            var streams = new HouseholdEventStreams();
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
            var streams = new HouseholdEventStreams();
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
            var streams = new HouseholdEventStreams();
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
            var streams = new HouseholdEventStreams();
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
            var streams = new HouseholdEventStreams();
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session-a", new SseEmitter()));

            // when
            var connections = streams.connectionsOfSession("session-b");

            // then
            assertThat(connections).isEmpty();
        }
    }

    @Nested
    class concurrentAccess {

        private static final int CONNECTION_COUNT = 10_000;
        private static final int THREAD_COUNT = 8;

        @Test
        void registerAndRemoveInParallel_exactlyTheKeptConnectionsListed() {
            // given
            var streams = new HouseholdEventStreams();
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

        private List<StreamConnection> connectionsOf(UUID householdId) {
            return IntStream.range(0, CONNECTION_COUNT)
                    .mapToObj(index -> new StreamConnection(householdId, UUID.randomUUID(), "session-" + index,
                            new SseEmitter()))
                    .toList();
        }
    }
}
