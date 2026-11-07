package eu.wiegandt.librehousehold.realtime.service;

import eu.wiegandt.librehousehold.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.realtime.model.StreamConnection;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps open streams alive and ends those whose session ended (RT3). {@code expireNow()} (password
 * reset, removed member) destroys no session, so no event reports it; an open stream sends no request
 * the {@code ConcurrentSessionFilter} could reject. The heartbeat is therefore the safety net that
 * ends such a stream at the latest one interval later.
 *
 * <p>Checks the session only through the {@link SessionRegistry}, never through the
 * {@code HttpSession}: a heartbeat must not count as activity and extend the session (RT4).
 */
@Component
class StreamHeartbeat {

    private final HouseholdEventStreams streams;
    private final SignalSender sender;
    private final SessionRegistry sessionRegistry;

    StreamHeartbeat(HouseholdEventStreams streams, SignalSender sender, SessionRegistry sessionRegistry) {
        this.streams = streams;
        this.sender = sender;
        this.sessionRegistry = sessionRegistry;
    }

    @Scheduled(fixedDelayString = "${librehousehold.realtime.heartbeat-interval}")
    void tick() {
        var alive = new ArrayList<StreamConnection>();
        streams.connections().forEach(connection -> closeOrKeep(connection, alive));
        sender.sendHeartbeat(alive);
    }

    private void closeOrKeep(StreamConnection connection, List<StreamConnection> alive) {
        var session = sessionRegistry.getSessionInformation(connection.sessionId());
        if (session == null) {
            close(connection, CloseReason.SESSION_ENDED);
            return;
        }
        if (session.isExpired()) {
            close(connection, CloseReason.SESSION_EXPIRED);
            return;
        }
        alive.add(connection);
    }

    // Completes without an event: the client notices the end through its next request (GET /me)
    private void close(StreamConnection connection, CloseReason reason) {
        streams.remove(connection, reason);
        connection.emitter().complete();
    }
}
