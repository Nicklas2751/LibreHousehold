package eu.wiegandt.librehousehold.realtime.service;

import eu.wiegandt.librehousehold.realtime.model.InvalidationSignal;
import eu.wiegandt.librehousehold.realtime.model.SignalEntity;
import eu.wiegandt.librehousehold.realtime.model.SignalType;
import eu.wiegandt.librehousehold.realtime.model.StreamConnection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SignalSenderTest {

    @Nested
    class send {

        static Stream<Exception> sendFailures() {
            return Stream.of(new IOException("Broken pipe"), new IllegalStateException("Emitter already completed"));
        }


        @Test
        void twoConnections_bothReceiveThePayload() throws IOException {
            // given
            var streams = new HouseholdEventStreams();
            var sender = new SignalSender(new SyncTaskExecutor(), streams);
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
            var sender = new SignalSender(new SyncTaskExecutor(), new HouseholdEventStreams());
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
            var sender = new SignalSender(new SyncTaskExecutor(), new HouseholdEventStreams());
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
            var streams = new HouseholdEventStreams();
            var sender = new SignalSender(new SyncTaskExecutor(), streams);
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
            var sender = new SignalSender(new SyncTaskExecutor(), new HouseholdEventStreams());
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
        void executorHasNotRunTheTaskYet_nothingSentInTheCallingThread() {
            // given
            var pendingTasks = new ArrayList<Runnable>();
            var sender = new SignalSender(pendingTasks::add, new HouseholdEventStreams());
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
        void connection_heartbeatCommentFromContract() throws IOException {
            // given
            var sender = new SignalSender(new SyncTaskExecutor(), new HouseholdEventStreams());
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
            var streams = new HouseholdEventStreams();
            var sender = new SignalSender(new SyncTaskExecutor(), streams);
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
            var sender = new SignalSender(pendingTasks::add, new HouseholdEventStreams());
            var emitter = mock(SseEmitter.class);

            // when
            sender.sendHeartbeat(List.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", emitter)));

            // then
            verifyNoInteractions(emitter);
        }
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
}
