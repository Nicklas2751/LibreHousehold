package eu.wiegandt.librehousehold.realtime.service;

import eu.wiegandt.librehousehold.realtime.config.RealtimeProperties;
import eu.wiegandt.librehousehold.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.realtime.model.StreamConnection;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class StreamHeartbeatTest {

    private static final String PRINCIPAL = "member";

    @Mock
    private SessionRegistry sessionRegistry;

    @Nested
    class tick {

        static List<Arguments> endedSessions() {
            return List.of(
                    argumentSet("session no longer registered", null, CloseReason.SESSION_ENDED),
                    argumentSet("session expired", expiredSession("session"), CloseReason.SESSION_EXPIRED));
        }

        @Test
        void validSession_heartbeatCommentSent() throws IOException {
            // given
            var streams = newStreams();
            var emitter = mock(SseEmitter.class);
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", emitter));
            doReturn(new SessionInformation(PRINCIPAL, "session", new Date())).when(sessionRegistry)
                    .getSessionInformation("session");

            // when
            new StreamHeartbeat(streams, newSender(new SyncTaskExecutor(), streams), sessionRegistry).tick();

            // then
            var event = ArgumentCaptor.forClass(SseEventBuilder.class);
            verify(emitter).send(event.capture());
            assertThat(text(event.getValue())).isEqualTo(": heartbeat\n\n");
        }

        @ParameterizedTest
        @MethodSource("endedSessions")
        void endedSession_connectionRemovedWithReason(SessionInformation session, CloseReason expectedReason) {
            // given
            var streams = spy(newStreams());
            var connection = new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session",
                    mock(SseEmitter.class));
            streams.register(connection);
            doReturn(session).when(sessionRegistry).getSessionInformation("session");

            // when
            new StreamHeartbeat(streams, newSender(new SyncTaskExecutor(), streams), sessionRegistry).tick();

            // then
            verify(streams).remove(connection, expectedReason);
        }

        @ParameterizedTest
        @MethodSource("endedSessions")
        void endedSession_streamCompletedWithoutEvent(SessionInformation session, CloseReason ignoredReason) {
            // given
            var streams = newStreams();
            var emitter = mock(SseEmitter.class);
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session", emitter));
            doReturn(session).when(sessionRegistry).getSessionInformation("session");

            // when
            new StreamHeartbeat(streams, newSender(new SyncTaskExecutor(), streams), sessionRegistry).tick();

            // then
            verify(emitter).complete();
            verifyNoMoreInteractions(emitter);
        }

        @Test
        void oneConnectionFails_otherConnectionStillReceivesHeartbeat() throws IOException {
            // given
            var streams = newStreams();
            var householdId = UUID.randomUUID();
            var failingEmitter = mock(SseEmitter.class);
            var healthyEmitter = mock(SseEmitter.class);
            doThrow(new IOException("Broken pipe")).when(failingEmitter).send(any(SseEventBuilder.class));
            streams.register(new StreamConnection(householdId, UUID.randomUUID(), "session-a", failingEmitter));
            streams.register(new StreamConnection(householdId, UUID.randomUUID(), "session-b", healthyEmitter));
            doReturn(new SessionInformation(PRINCIPAL, "session-a", new Date())).when(sessionRegistry)
                    .getSessionInformation("session-a");
            doReturn(new SessionInformation(PRINCIPAL, "session-b", new Date())).when(sessionRegistry)
                    .getSessionInformation("session-b");

            // when
            new StreamHeartbeat(streams, newSender(new SyncTaskExecutor(), streams), sessionRegistry).tick();

            // then
            verify(healthyEmitter).send(any(SseEventBuilder.class));
        }

        @Test
        void validSession_lastRequestOfSessionUnchanged() {
            // given
            var streams = newStreams();
            streams.register(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), "session",
                    mock(SseEmitter.class)));
            var lastRequest = Date.from(Instant.parse("2026-11-07T18:00:00Z"));
            var session = new SessionInformation(PRINCIPAL, "session", lastRequest);
            doReturn(session).when(sessionRegistry).getSessionInformation("session");

            // when
            new StreamHeartbeat(streams, newSender(new SyncTaskExecutor(), streams), sessionRegistry).tick();

            // then
            assertThat(session.getLastRequest()).isEqualTo(lastRequest);
        }
    }

    private static SessionInformation expiredSession(String sessionId) {
        var session = new SessionInformation(PRINCIPAL, sessionId, new Date());
        session.expireNow();
        return session;
    }

    private String text(SseEventBuilder event) {
        return event.build().stream()
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
