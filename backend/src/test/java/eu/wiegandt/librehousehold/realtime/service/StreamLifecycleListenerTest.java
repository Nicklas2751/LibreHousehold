package eu.wiegandt.librehousehold.realtime.service;

import eu.wiegandt.librehousehold.household.HouseholdDeleted;
import eu.wiegandt.librehousehold.household.MemberRemoved;
import eu.wiegandt.librehousehold.realtime.config.RealtimeProperties;
import eu.wiegandt.librehousehold.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.realtime.model.InvalidationSignal;
import eu.wiegandt.librehousehold.realtime.model.SignalEntity;
import eu.wiegandt.librehousehold.realtime.model.StreamConnection;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class StreamLifecycleListenerTest {

    private static final String COMPLETED = "complete";
    private static final String EVENT_FIELD = "event:";
    private static final String HOUSEHOLD_NAME = "Musterhaushalt";

    @Nested
    class closeStreamsOfRemovedMember {

        @Test
        void connectionOfMember_sessionTerminatedThenCompleted() throws IOException {
            // given
            var streams = newStreams();
            var memberId = UUID.randomUUID();
            var emitter = mock(SseEmitter.class);
            var calls = recordCalls(emitter);
            streams.register(new StreamConnection(UUID.randomUUID(), memberId, "session", emitter));
            var listener = new StreamLifecycleListener(streams, newSender(new SyncTaskExecutor(), streams));

            // when
            listener.closeStreamsOfRemovedMember(
                    new MemberRemoved(memberId, "Anna", "anna@example.com", HOUSEHOLD_NAME));

            // then
            assertThat(calls).containsExactly("session.terminated", COMPLETED);
        }

        @Test
        void connectionOfMember_removedFromRegistryWithReasonMemberRemoved() {
            // given
            var streams = spy(newStreams());
            var memberId = UUID.randomUUID();
            var connection = new StreamConnection(UUID.randomUUID(), memberId, "session", mock(SseEmitter.class));
            streams.register(connection);
            var listener = new StreamLifecycleListener(streams, newSender(new SyncTaskExecutor(), streams));

            // when
            listener.closeStreamsOfRemovedMember(
                    new MemberRemoved(memberId, "Anna", "anna@example.com", HOUSEHOLD_NAME));

            // then
            verify(streams).remove(connection, CloseReason.MEMBER_REMOVED);
        }

        @Test
        void connectionOfOtherMember_untouched() {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var otherEmitter = mock(SseEmitter.class);
            streams.register(new StreamConnection(householdId, UUID.randomUUID(), "session-other", otherEmitter));
            var listener = new StreamLifecycleListener(streams, newSender(new SyncTaskExecutor(), streams));

            // when
            listener.closeStreamsOfRemovedMember(
                    new MemberRemoved(UUID.randomUUID(), "Anna", "anna@example.com", HOUSEHOLD_NAME));

            // then
            verifyNoInteractions(otherEmitter);
        }

        @Test
        void noConnection_noException() {
            // given
            var streams = newStreams();
            var listener = new StreamLifecycleListener(streams, newSender(new SyncTaskExecutor(), streams));

            // when / then
            assertThatCode(() -> listener.closeStreamsOfRemovedMember(
                    new MemberRemoved(UUID.randomUUID(), "Anna", "anna@example.com", HOUSEHOLD_NAME)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class closeStreamsOfDeletedHousehold {

        @Test
        void connectionOfHouseholdAndReorderingExecutor_householdDeletedThenSessionTerminatedThenCompleted()
                throws IOException {
            // given
            var streams = newStreams();
            var pendingTasks = new ArrayList<Runnable>();
            var householdId = UUID.randomUUID();
            var emitter = mock(SseEmitter.class);
            var calls = recordCalls(emitter);
            streams.register(new StreamConnection(householdId, UUID.randomUUID(), "session", emitter));
            var listener = new StreamLifecycleListener(streams, newSender(pendingTasks::add, streams));

            // when
            listener.closeStreamsOfDeletedHousehold(new HouseholdDeleted(householdId, HOUSEHOLD_NAME, List.of()));
            pendingTasks.reversed().forEach(Runnable::run);

            // then
            assertThat(calls).containsExactly("household.deleted", "session.terminated", COMPLETED);
        }

        @Test
        void connectionOfHousehold_householdDeletedReferencesTheHousehold() throws IOException {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var emitter = mock(SseEmitter.class);
            streams.register(new StreamConnection(householdId, UUID.randomUUID(), "session", emitter));
            var listener = new StreamLifecycleListener(streams, newSender(new SyncTaskExecutor(), streams));
            var expected = new InvalidationSignal(SignalEntity.HOUSEHOLD, householdId, null);

            // when
            listener.closeStreamsOfDeletedHousehold(new HouseholdDeleted(householdId, HOUSEHOLD_NAME, List.of()));

            // then
            var events = ArgumentCaptor.forClass(SseEventBuilder.class);
            verify(emitter, times(2)).send(events.capture());
            assertThat(payload(events.getAllValues().getFirst()))
                    .usingRecursiveComparison().ignoringFields("occurredAt").isEqualTo(expected);
        }

        @Test
        void connectionOfHousehold_removedFromRegistryWithReasonHouseholdDeleted() {
            // given
            var streams = spy(newStreams());
            var householdId = UUID.randomUUID();
            var connection = new StreamConnection(householdId, UUID.randomUUID(), "session", mock(SseEmitter.class));
            streams.register(connection);
            var listener = new StreamLifecycleListener(streams, newSender(new SyncTaskExecutor(), streams));

            // when
            listener.closeStreamsOfDeletedHousehold(new HouseholdDeleted(householdId, HOUSEHOLD_NAME, List.of()));

            // then
            verify(streams).remove(connection, CloseReason.HOUSEHOLD_DELETED);
        }

        @Test
        void connectionOfOtherHousehold_untouched() {
            // given
            var streams = newStreams();
            var otherEmitter = mock(SseEmitter.class);
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", otherEmitter));
            var listener = new StreamLifecycleListener(streams, newSender(new SyncTaskExecutor(), streams));

            // when
            listener.closeStreamsOfDeletedHousehold(
                    new HouseholdDeleted(UUID.randomUUID(), HOUSEHOLD_NAME, List.of()));

            // then
            verifyNoInteractions(otherEmitter);
        }

        @Test
        void noConnection_noException() {
            // given
            var streams = newStreams();
            var listener = new StreamLifecycleListener(streams, newSender(new SyncTaskExecutor(), streams));

            // when / then
            assertThatCode(() -> listener.closeStreamsOfDeletedHousehold(
                    new HouseholdDeleted(UUID.randomUUID(), HOUSEHOLD_NAME, List.of())))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class closeStreamsOfEndedSession {

        @Test
        void connectionOfSession_completedWithoutEvent() {
            // given
            var streams = newStreams();
            var emitter = mock(SseEmitter.class);
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", emitter));
            var listener = new StreamLifecycleListener(streams, newSender(new SyncTaskExecutor(), streams));

            // when
            listener.closeStreamsOfEndedSession(new HttpSessionDestroyedEvent(new MockHttpSession(null, "session")));

            // then
            verify(emitter).complete();
            verifyNoMoreInteractions(emitter);
        }

        @Test
        void connectionOfSession_removedFromRegistryWithReasonSessionEnded() {
            // given
            var streams = spy(newStreams());
            var connection = new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session",
                    mock(SseEmitter.class));
            streams.register(connection);
            var listener = new StreamLifecycleListener(streams, newSender(new SyncTaskExecutor(), streams));

            // when
            listener.closeStreamsOfEndedSession(new HttpSessionDestroyedEvent(new MockHttpSession(null, "session")));

            // then
            verify(streams).remove(connection, CloseReason.SESSION_ENDED);
        }

        @Test
        void connectionOfOtherSession_untouched() {
            // given
            var streams = newStreams();
            var otherEmitter = mock(SseEmitter.class);
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session-other",
                    otherEmitter));
            var listener = new StreamLifecycleListener(streams, newSender(new SyncTaskExecutor(), streams));

            // when
            listener.closeStreamsOfEndedSession(new HttpSessionDestroyedEvent(new MockHttpSession(null, "session")));

            // then
            verifyNoInteractions(otherEmitter);
        }

        @Test
        void noConnection_noException() {
            // given
            var streams = newStreams();
            var listener = new StreamLifecycleListener(streams, newSender(new SyncTaskExecutor(), streams));

            // when / then
            assertThatCode(() -> listener.closeStreamsOfEndedSession(
                    new HttpSessionDestroyedEvent(new MockHttpSession(null, "session"))))
                    .doesNotThrowAnyException();
        }
    }

    // Records the event names sent to the emitter and its completion, in the order they happen
    private List<String> recordCalls(SseEmitter emitter) throws IOException {
        var calls = new ArrayList<String>();
        doAnswer(invocation -> calls.add(eventName(invocation.getArgument(0)))).when(emitter)
                .send(any(SseEventBuilder.class));
        doAnswer(invocation -> calls.add(COMPLETED)).when(emitter).complete();
        return calls;
    }

    private String eventName(SseEventBuilder event) {
        var text = text(event);
        return text.substring(EVENT_FIELD.length(), text.indexOf('\n'));
    }

    private Object payload(SseEventBuilder event) {
        return event.build().stream()
                .filter(part -> MediaType.APPLICATION_JSON.equals(part.getMediaType()))
                .map(DataWithMediaType::getData)
                .findFirst()
                .orElseThrow();
    }

    private String text(SseEventBuilder event) {
        return event.build().stream()
                .filter(part -> !MediaType.APPLICATION_JSON.equals(part.getMediaType()))
                .map(part -> String.valueOf(part.getData()))
                .collect(Collectors.joining());
    }

    // The defaults of application.yaml; only the limit tests of HouseholdEventStreamsTest need others
    private HouseholdEventStreams newStreams() {
        return new HouseholdEventStreams(new RealtimeProperties(Duration.ofSeconds(5), Duration.ofMinutes(25), 5, 10,
                500, Duration.ofSeconds(30)), new StreamMetrics(new SimpleMeterRegistry()));
    }

    private SignalSender newSender(TaskExecutor executor, HouseholdEventStreams streams) {
        return new SignalSender(executor, streams, new StreamMetrics(new SimpleMeterRegistry()));
    }
}
