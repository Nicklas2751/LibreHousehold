package eu.wiegandt.librehousehold.notifications.realtime.service;

import eu.wiegandt.librehousehold.notifications.realtime.config.RealtimeProperties;
import eu.wiegandt.librehousehold.notifications.realtime.exception.StreamLimitExceededProblem;
import eu.wiegandt.librehousehold.notifications.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.notifications.realtime.model.StreamConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
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
 *
 * <p>Opening, closing and rejecting a stream are logged with member and household only, never with
 * the session ID: it is the credential of the session (RT10).
 */
@Component
public class HouseholdEventStreams {

    private static final Logger LOG = LoggerFactory.getLogger(HouseholdEventStreams.class);
    private static final String GLOBAL_LIMIT = "global";
    private static final String MEMBER_LIMIT = "per member";
    private static final String SESSION_LIMIT = "per session";

    private final Set<StreamConnection> connections = ConcurrentHashMap.newKeySet();
    private final RealtimeProperties properties;
    private final StreamMetrics metrics;

    public HouseholdEventStreams(RealtimeProperties properties, StreamMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * Synchronized, so parallel registrations cannot all pass the limit check before any of them is
     * added (RT5). Removing needs no lock: a concurrent removal only frees capacity.
     *
     * @throws StreamLimitExceededProblem if the session, the member or this instance already has
     *                                    the maximum number of open streams; the connection is not
     *                                    registered then
     */
    public synchronized void register(StreamConnection connection) {
        reachedLimit(connection).ifPresent(limit -> reject(connection, limit));
        connections.add(connection);
        metrics.streamOpened();
        LOG.info("Event stream of member {} in household {} opened", connection.memberId(), connection.householdId());
    }

    /**
     * Idempotent: the emitter reports an error or a timeout and its completion afterwards, so a
     * connection is usually removed twice; only the first reason counts.
     */
    public void remove(StreamConnection connection, CloseReason reason) {
        if (connections.remove(connection)) {
            metrics.streamClosed(reason);
            LOG.info("Event stream of member {} in household {} closed: {}", connection.memberId(),
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

    public Set<StreamConnection> connections() {
        return Set.copyOf(connections);
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

    private Optional<String> reachedLimit(StreamConnection connection) {
        if (connections.size() >= properties.maxStreams()) {
            return Optional.of(GLOBAL_LIMIT);
        }
        if (connectionsOfMember(connection.memberId()).size() >= properties.maxStreamsPerMember()) {
            return Optional.of(MEMBER_LIMIT);
        }
        if (connectionsOfSession(connection.sessionId()).size() >= properties.maxStreamsPerSession()) {
            return Optional.of(SESSION_LIMIT);
        }
        return Optional.empty();
    }

    private void reject(StreamConnection connection, String limit) {
        LOG.warn("Event stream of member {} in household {} rejected: limit {} reached", connection.memberId(),
                connection.householdId(), limit);
        throw new StreamLimitExceededProblem(properties.retryAfter());
    }

    private void closeForShutdown(StreamConnection connection) {
        remove(connection, CloseReason.SHUTDOWN);
        connection.emitter().complete();
    }

    private Set<StreamConnection> connectionsMatching(Predicate<StreamConnection> filter) {
        return connections.stream().filter(filter).collect(Collectors.toUnmodifiableSet());
    }
}
