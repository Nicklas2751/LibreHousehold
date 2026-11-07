package eu.wiegandt.librehousehold.realtime.service;

import eu.wiegandt.librehousehold.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.realtime.model.StreamConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The open event streams of this instance. Lives in process memory like sessions do (ADR-019).
 *
 * <p>A single flat set instead of an index per household, member and session: households are small
 * and the number of open streams is bounded, so filtering is cheap and there is no index to keep
 * consistent between threads. Queries return snapshots, so callers may iterate them while streams
 * open and close.
 */
@Component
public class HouseholdEventStreams {

    private static final Logger LOG = LoggerFactory.getLogger(HouseholdEventStreams.class);

    private final Set<StreamConnection> connections = ConcurrentHashMap.newKeySet();

    public void register(StreamConnection connection) {
        connections.add(connection);
    }

    /**
     * Idempotent: the emitter reports an error or a timeout and its completion afterwards, so a
     * connection is usually removed twice; only the first reason counts.
     */
    public void remove(StreamConnection connection, CloseReason reason) {
        if (connections.remove(connection)) {
            LOG.debug("Event stream of member {} in household {} closed: {}", connection.memberId(),
                    connection.householdId(), reason);
        }
    }

    /**
     * Ends every stream when the application shuts down. Tomcat's graceful shutdown waits for active
     * requests, and an open stream stays active until its emitter timeout, so without this every
     * shutdown would hang until the shutdown phase times out.
     */
    @EventListener(ContextClosedEvent.class)
    public void closeAll() {
        connections.forEach(this::closeForShutdown);
    }

    public Set<StreamConnection> connectionsOf(UUID householdId) {
        return connectionsMatching(connection -> connection.householdId().equals(householdId));
    }

    public Set<StreamConnection> connectionsOfMember(UUID memberId) {
        return connectionsMatching(connection -> connection.memberId().equals(memberId));
    }

    public Set<StreamConnection> connectionsOfSession(String sessionId) {
        return connectionsMatching(connection -> connection.sessionId().equals(sessionId));
    }

    private void closeForShutdown(StreamConnection connection) {
        remove(connection, CloseReason.SHUTDOWN);
        connection.emitter().complete();
    }

    private Set<StreamConnection> connectionsMatching(Predicate<StreamConnection> filter) {
        return connections.stream().filter(filter).collect(Collectors.toUnmodifiableSet());
    }
}
