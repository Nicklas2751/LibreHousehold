package eu.wiegandt.librehousehold.notifications.realtime.service;

import eu.wiegandt.librehousehold.notifications.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.notifications.realtime.model.StreamConnection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class StreamHeartbeatTest {

    private static final String PRINCIPAL = "member";
    private static final String SESSION_ID = "session";

    @Mock
    private HouseholdEventStreams streams;

    @Mock
    private SignalSender sender;

    @Mock
    private SessionRegistry sessionRegistry;

    @InjectMocks
    private StreamHeartbeat heartbeat;

    @Nested
    class tick {

        static List<Arguments> endedSessions() {
            return List.of(
                    argumentSet("session no longer registered", null, CloseReason.SESSION_ENDED),
                    argumentSet("session expired", expiredSession(SESSION_ID), CloseReason.SESSION_EXPIRED));
        }

        @Test
        void validSession_heartbeatSentToConnection() {
            // given
            var connection = connection(SESSION_ID);
            doReturn(Set.of(connection)).when(streams).connections();
            doReturn(new SessionInformation(PRINCIPAL, SESSION_ID, new Date())).when(sessionRegistry)
                    .getSessionInformation(SESSION_ID);

            // when
            heartbeat.tick();

            // then
            verify(sender).sendHeartbeat(List.of(connection));
        }

        @Test
        void validAndEndedSession_heartbeatSentOnlyToConnectionOfValidSession() {
            // given
            var validConnection = connection("session-valid");
            doReturn(Set.of(validConnection, connection("session-ended"))).when(streams).connections();
            doReturn(new SessionInformation(PRINCIPAL, "session-valid", new Date())).when(sessionRegistry)
                    .getSessionInformation("session-valid");
            doReturn(null).when(sessionRegistry).getSessionInformation("session-ended");

            // when
            heartbeat.tick();

            // then
            verify(sender).sendHeartbeat(List.of(validConnection));
        }

        @ParameterizedTest
        @MethodSource("endedSessions")
        void endedSession_connectionRemovedWithReason(SessionInformation session, CloseReason expectedReason) {
            // given
            var connection = connection(SESSION_ID);
            doReturn(Set.of(connection)).when(streams).connections();
            doReturn(session).when(sessionRegistry).getSessionInformation(SESSION_ID);

            // when
            heartbeat.tick();

            // then
            verify(streams).remove(connection, expectedReason);
        }

        @ParameterizedTest
        @MethodSource("endedSessions")
        void endedSession_streamCompletedWithoutEvent(SessionInformation session, CloseReason ignoredReason) {
            // given
            var emitter = mock(SseEmitter.class);
            doReturn(Set.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), SESSION_ID, emitter)))
                    .when(streams).connections();
            doReturn(session).when(sessionRegistry).getSessionInformation(SESSION_ID);

            // when
            heartbeat.tick();

            // then
            verify(emitter).complete();
            verifyNoMoreInteractions(emitter);
        }

        @Test
        void validSession_lastRequestOfSessionUnchanged() {
            // given
            doReturn(Set.of(connection(SESSION_ID))).when(streams).connections();
            var lastRequest = Date.from(Instant.parse("2026-11-07T18:00:00Z"));
            var session = new SessionInformation(PRINCIPAL, SESSION_ID, lastRequest);
            doReturn(session).when(sessionRegistry).getSessionInformation(SESSION_ID);

            // when
            heartbeat.tick();

            // then
            assertThat(session.getLastRequest()).isEqualTo(lastRequest);
        }
    }

    private static SessionInformation expiredSession(String sessionId) {
        var session = new SessionInformation(PRINCIPAL, sessionId, new Date());
        session.expireNow();
        return session;
    }

    private StreamConnection connection(String sessionId) {
        return new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), sessionId, mock(SseEmitter.class));
    }
}
