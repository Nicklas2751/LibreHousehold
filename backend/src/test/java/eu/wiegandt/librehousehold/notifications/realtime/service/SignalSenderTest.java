package eu.wiegandt.librehousehold.notifications.realtime.service;

import eu.wiegandt.librehousehold.notifications.realtime.config.RealtimeProperties;
import eu.wiegandt.librehousehold.notifications.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.notifications.realtime.model.InvalidationSignal;
import eu.wiegandt.librehousehold.notifications.realtime.model.SessionTerminatedSignal;
import eu.wiegandt.librehousehold.notifications.realtime.model.SignalEntity;
import eu.wiegandt.librehousehold.notifications.realtime.model.SignalMessage;
import eu.wiegandt.librehousehold.notifications.realtime.model.SignalType;
import eu.wiegandt.librehousehold.notifications.realtime.model.StreamConnection;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class SignalSenderTest {

    private static final String COMPLETED = "complete";
    private static final String EVENT_FIELD = "event:";
    private static final String SENT_SIGNALS = "librehousehold.notifications.realtime.signals.sent";
    private static final String TYPE_TAG = "type";

    @Nested
    class send {

        static Stream<Exception> sendFailures() {
            return Stream.of(new IOException("Broken pipe"), new IllegalStateException("Emitter already completed"));
        }


        @Test
        void twoConnections_bothReceiveThePayload() throws IOException {
            // given
            var streams = newStreams();
            var sender = newSender(new SyncTaskExecutor(), streams);
            var householdId = UUID.randomUUID();
            var firstEmitter = mock(SseEmitter.class);
            var secondEmitter = mock(SseEmitter.class);
            var signal = new InvalidationSignal(SignalEntity.TASK, UUID.randomUUID(), Instant.now());

            // when
            sender.send(SignalType.TASK_CREATED, signal, List.of(
                    new StreamConnection(householdId, UUID.randomUUID(), "session-a", firstEmitter),
                    new StreamConnection(householdId, UUID.randomUUID(), "session-b", secondEmitter)));

            // then
            var events = ArgumentCaptor.forClass(SseEventBuilder.class);
            verify(firstEmitter).send(events.capture());
            verify(secondEmitter).send(events.capture());
            assertThat(events.getAllValues()).extracting(SignalSenderTest.this::payload).containsExactly(signal, signal);
        }

        @Test
        void signalType_eventNamedAfterWireName() throws IOException {
            // given
            var sender = newSender(new SyncTaskExecutor(), newStreams());
            var emitter = mock(SseEmitter.class);

            // when
            sender.send(SignalType.TASK_CREATED,
                    new InvalidationSignal(SignalEntity.TASK, UUID.randomUUID(), Instant.now()),
                    List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", emitter)));

            // then
            var event = ArgumentCaptor.forClass(SseEventBuilder.class);
            verify(emitter).send(event.capture());
            assertThat(text(event.getValue())).contains("event:task.created\n");
        }

        @ParameterizedTest
        @MethodSource("sendFailures")
        void oneConnectionFails_otherConnectionStillReceivesThePayload(Exception failure) throws IOException {
            // given
            var sender = newSender(new SyncTaskExecutor(), newStreams());
            var householdId = UUID.randomUUID();
            var failingEmitter = mock(SseEmitter.class);
            var healthyEmitter = mock(SseEmitter.class);
            doThrow(failure).when(failingEmitter).send(any(SseEventBuilder.class));
            var signal = new InvalidationSignal(SignalEntity.TASK, UUID.randomUUID(), Instant.now());

            // when
            sender.send(SignalType.TASK_CREATED, signal, List.of(
                    new StreamConnection(householdId, UUID.randomUUID(), "session-a", failingEmitter),
                    new StreamConnection(householdId, UUID.randomUUID(), "session-b", healthyEmitter)));

            // then
            var event = ArgumentCaptor.forClass(SseEventBuilder.class);
            verify(healthyEmitter).send(event.capture());
            assertThat(payload(event.getValue())).isEqualTo(signal);
        }

        @ParameterizedTest
        @MethodSource("sendFailures")
        void connectionFails_failedConnectionRemovedFromRegistry(Exception failure) throws IOException {
            // given
            var streams = newStreams();
            var sender = newSender(new SyncTaskExecutor(), streams);
            var householdId = UUID.randomUUID();
            var failingEmitter = mock(SseEmitter.class);
            doThrow(failure).when(failingEmitter).send(any(SseEventBuilder.class));
            var failing = new StreamConnection(householdId, UUID.randomUUID(), "session-a", failingEmitter);
            var healthy = new StreamConnection(householdId, UUID.randomUUID(), "session-b", mock(SseEmitter.class));
            streams.register(failing);
            streams.register(healthy);

            // when
            sender.send(SignalType.TASK_CREATED,
                    new InvalidationSignal(SignalEntity.TASK, UUID.randomUUID(), Instant.now()), List.of(failing, healthy));

            // then
            assertThat(streams.connectionsOf(householdId)).containsExactly(healthy);
        }

        @ParameterizedTest
        @MethodSource("sendFailures")
        void connectionFails_failedEmitterCompletedWithError(Exception failure) throws IOException {
            // given
            var sender = newSender(new SyncTaskExecutor(), newStreams());
            var failingEmitter = mock(SseEmitter.class);
            doThrow(failure).when(failingEmitter).send(any(SseEventBuilder.class));

            // when
            sender.send(SignalType.TASK_CREATED,
                    new InvalidationSignal(SignalEntity.TASK, UUID.randomUUID(), Instant.now()),
                    List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", failingEmitter)));

            // then
            verify(failingEmitter).completeWithError(failure);
        }

        @Test
        void twoConnectionsDelivered_signalCountedTwiceForItsType() {
            // given
            var registry = new SimpleMeterRegistry();
            var sender = new SignalSender(new SyncTaskExecutor(), newStreams(), new StreamMetrics(registry));

            // when
            sender.send(SignalType.TASK_CREATED,
                    new InvalidationSignal(SignalEntity.TASK, UUID.randomUUID(), Instant.now()),
                    List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session-a",
                                    mock(SseEmitter.class)),
                            new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session-b",
                                    mock(SseEmitter.class))));

            // then
            assertThat(registry.get(SENT_SIGNALS).tag(TYPE_TAG, "task.created").counter().count()).isEqualTo(2);
        }

        @ParameterizedTest
        @MethodSource("sendFailures")
        void connectionFails_signalNotCounted(Exception failure) throws IOException {
            // given
            var registry = new SimpleMeterRegistry();
            var sender = new SignalSender(new SyncTaskExecutor(), newStreams(), new StreamMetrics(registry));
            var failingEmitter = mock(SseEmitter.class);
            doThrow(failure).when(failingEmitter).send(any(SseEventBuilder.class));

            // when
            sender.send(SignalType.TASK_CREATED,
                    new InvalidationSignal(SignalEntity.TASK, UUID.randomUUID(), Instant.now()),
                    List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", failingEmitter)));

            // then
            assertThat(sentSignals(registry)).isZero();
        }

        @Test
        void executorHasNotRunTheTaskYet_nothingSentInTheCallingThread() {
            // given
            var pendingTasks = new ArrayList<Runnable>();
            var sender = newSender(pendingTasks::add, newStreams());
            var emitter = mock(SseEmitter.class);

            // when
            sender.send(SignalType.TASK_CREATED,
                    new InvalidationSignal(SignalEntity.TASK, UUID.randomUUID(), Instant.now()),
                    List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", emitter)));

            // then
            verifyNoInteractions(emitter);
        }
    }

    @Nested
    class sendHeartbeat {

        static Stream<Exception> sendFailures() {
            return Stream.of(new IOException("Broken pipe"), new IllegalStateException("Emitter already completed"));
        }

        @Test
        void connection_noSignalCounted() {
            // given
            var registry = new SimpleMeterRegistry();
            var sender = new SignalSender(new SyncTaskExecutor(), newStreams(), new StreamMetrics(registry));

            // when
            sender.sendHeartbeat(List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session",
                    mock(SseEmitter.class))));

            // then
            assertThat(sentSignals(registry)).isZero();
        }

        @Test
        void connection_heartbeatCommentFromContract() throws IOException {
            // given
            var sender = newSender(new SyncTaskExecutor(), newStreams());
            var emitter = mock(SseEmitter.class);

            // when
            sender.sendHeartbeat(List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", emitter)));

            // then
            var event = ArgumentCaptor.forClass(SseEventBuilder.class);
            verify(emitter).send(event.capture());
            assertThat(text(event.getValue())).isEqualTo(": heartbeat\n\n");
        }

        @ParameterizedTest
        @MethodSource("sendFailures")
        void connectionFails_failedConnectionRemovedFromRegistry(Exception failure) throws IOException {
            // given
            var streams = newStreams();
            var sender = newSender(new SyncTaskExecutor(), streams);
            var householdId = UUID.randomUUID();
            var failingEmitter = mock(SseEmitter.class);
            doThrow(failure).when(failingEmitter).send(any(SseEventBuilder.class));
            var failing = new StreamConnection(householdId, UUID.randomUUID(), "session-a", failingEmitter);
            var healthy = new StreamConnection(householdId, UUID.randomUUID(), "session-b", mock(SseEmitter.class));
            streams.register(failing);
            streams.register(healthy);

            // when
            sender.sendHeartbeat(List.of(failing, healthy));

            // then
            assertThat(streams.connectionsOf(householdId)).containsExactly(healthy);
        }

        @Test
        void executorHasNotRunTheTaskYet_nothingSentInTheCallingThread() {
            // given
            var pendingTasks = new ArrayList<Runnable>();
            var sender = newSender(pendingTasks::add, newStreams());
            var emitter = mock(SseEmitter.class);

            // when
            sender.sendHeartbeat(List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", emitter)));

            // then
            verifyNoInteractions(emitter);
        }
    }

    @Nested
    class sendAndClose {

        static Stream<Exception> sendFailures() {
            return Stream.of(new IOException("Broken pipe"), new IllegalStateException("Emitter already completed"));
        }

        @Test
        void twoMessagesAndReorderingExecutor_messagesInGivenOrderThenCompleted() throws IOException {
            // given
            var pendingTasks = new ArrayList<Runnable>();
            var sender = newSender(pendingTasks::add, newStreams());
            var emitter = mock(SseEmitter.class);
            var calls = recordCalls(emitter);
            var occurredAt = Instant.now();

            // when
            sender.sendAndClose(List.of(
                            new SignalMessage(SignalType.HOUSEHOLD_DELETED,
                                    new InvalidationSignal(SignalEntity.HOUSEHOLD, UUID.randomUUID(), occurredAt)),
                            new SignalMessage(SignalType.SESSION_TERMINATED, new SessionTerminatedSignal(occurredAt))),
                    List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", emitter)),
                    CloseReason.HOUSEHOLD_DELETED);
            pendingTasks.reversed().forEach(Runnable::run);

            // then
            assertThat(calls).containsExactly("household.deleted", "session.terminated", COMPLETED);
        }

        @Test
        void connection_removedFromRegistryWithReason() {
            // given
            var streams = spy(newStreams());
            var sender = newSender(new SyncTaskExecutor(), streams);
            var connection = new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session",
                    mock(SseEmitter.class));
            streams.register(connection);

            // when
            sender.sendAndClose(List.of(new SignalMessage(SignalType.SESSION_TERMINATED,
                    new SessionTerminatedSignal(Instant.now()))), List.of(connection), CloseReason.MEMBER_REMOVED);

            // then
            verify(streams).remove(connection, CloseReason.MEMBER_REMOVED);
        }

        @Test
        void executorHasNotRunTheTaskYet_connectionAlreadyRemovedFromRegistry() {
            // given
            var pendingTasks = new ArrayList<Runnable>();
            var streams = newStreams();
            var sender = newSender(pendingTasks::add, streams);
            var householdId = UUID.randomUUID();
            var connection = new StreamConnection(householdId, UUID.randomUUID(), "session", mock(SseEmitter.class));
            streams.register(connection);

            // when
            sender.sendAndClose(List.of(new SignalMessage(SignalType.SESSION_TERMINATED,
                    new SessionTerminatedSignal(Instant.now()))), List.of(connection), CloseReason.MEMBER_REMOVED);

            // then
            assertThat(streams.connectionsOf(householdId)).isEmpty();
        }

        @Test
        void executorHasNotRunTheTaskYet_nothingSentInTheCallingThread() {
            // given
            var pendingTasks = new ArrayList<Runnable>();
            var sender = newSender(pendingTasks::add, newStreams());
            var emitter = mock(SseEmitter.class);

            // when
            sender.sendAndClose(List.of(new SignalMessage(SignalType.SESSION_TERMINATED,
                            new SessionTerminatedSignal(Instant.now()))),
                    List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", emitter)),
                    CloseReason.MEMBER_REMOVED);

            // then
            verifyNoInteractions(emitter);
        }

        @ParameterizedTest
        @MethodSource("sendFailures")
        void firstMessageFails_onlyCompletedWithError(Exception failure) throws IOException {
            // given
            var sender = newSender(new SyncTaskExecutor(), newStreams());
            var failingEmitter = mock(SseEmitter.class);
            doThrow(failure).when(failingEmitter).send(any(SseEventBuilder.class));
            var occurredAt = Instant.now();

            // when
            sender.sendAndClose(List.of(
                            new SignalMessage(SignalType.HOUSEHOLD_DELETED,
                                    new InvalidationSignal(SignalEntity.HOUSEHOLD, UUID.randomUUID(), occurredAt)),
                            new SignalMessage(SignalType.SESSION_TERMINATED, new SessionTerminatedSignal(occurredAt))),
                    List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", failingEmitter)),
                    CloseReason.HOUSEHOLD_DELETED);

            // then
            verify(failingEmitter).send(any(SseEventBuilder.class));
            verify(failingEmitter).completeWithError(failure);
            verifyNoMoreInteractions(failingEmitter);
        }

        @Test
        void oneConnectionFails_otherConnectionStillReceivesMessageAndIsCompleted() throws IOException {
            // given
            var sender = newSender(new SyncTaskExecutor(), newStreams());
            var householdId = UUID.randomUUID();
            var failingEmitter = mock(SseEmitter.class);
            doThrow(new IOException("Broken pipe")).when(failingEmitter).send(any(SseEventBuilder.class));
            var healthyEmitter = mock(SseEmitter.class);
            var calls = recordCalls(healthyEmitter);

            // when
            sender.sendAndClose(List.of(new SignalMessage(SignalType.SESSION_TERMINATED,
                            new SessionTerminatedSignal(Instant.now()))),
                    List.of(new StreamConnection(householdId, UUID.randomUUID(), "session-a", failingEmitter),
                            new StreamConnection(householdId, UUID.randomUUID(), "session-b", healthyEmitter)),
                    CloseReason.MEMBER_REMOVED);

            // then
            assertThat(calls).containsExactly("session.terminated", COMPLETED);
        }

        @Test
        void twoMessagesDelivered_eachCountedForItsType() {
            // given
            var registry = new SimpleMeterRegistry();
            var sender = new SignalSender(new SyncTaskExecutor(), newStreams(), new StreamMetrics(registry));
            var occurredAt = Instant.now();

            // when
            sender.sendAndClose(List.of(
                            new SignalMessage(SignalType.HOUSEHOLD_DELETED,
                                    new InvalidationSignal(SignalEntity.HOUSEHOLD, UUID.randomUUID(), occurredAt)),
                            new SignalMessage(SignalType.SESSION_TERMINATED, new SessionTerminatedSignal(occurredAt))),
                    List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session",
                            mock(SseEmitter.class))),
                    CloseReason.HOUSEHOLD_DELETED);

            // then
            assertThat(registry.find(SENT_SIGNALS).counters())
                    .extracting(counter -> counter.getId().getTag(TYPE_TAG), Counter::count)
                    .containsExactlyInAnyOrder(tuple("household.deleted", 1.0), tuple("session.terminated", 1.0));
        }

        @Test
        void firstMessageFails_nothingCounted() throws IOException {
            // given
            var registry = new SimpleMeterRegistry();
            var sender = new SignalSender(new SyncTaskExecutor(), newStreams(), new StreamMetrics(registry));
            var failingEmitter = mock(SseEmitter.class);
            doThrow(new IOException("Broken pipe")).when(failingEmitter).send(any(SseEventBuilder.class));
            var occurredAt = Instant.now();

            // when
            sender.sendAndClose(List.of(
                            new SignalMessage(SignalType.HOUSEHOLD_DELETED,
                                    new InvalidationSignal(SignalEntity.HOUSEHOLD, UUID.randomUUID(), occurredAt)),
                            new SignalMessage(SignalType.SESSION_TERMINATED, new SessionTerminatedSignal(occurredAt))),
                    List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", failingEmitter)),
                    CloseReason.HOUSEHOLD_DELETED);

            // then
            assertThat(sentSignals(registry)).isZero();
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

    // Sums every type, so a test also sees a signal counted under an unexpected type
    private double sentSignals(SimpleMeterRegistry registry) {
        return registry.find(SENT_SIGNALS).counters().stream().mapToDouble(Counter::count).sum();
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
