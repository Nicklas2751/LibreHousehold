package eu.wiegandt.librehousehold.notifications.realtime.service;

import eu.wiegandt.librehousehold.household.HouseholdDeleted;
import eu.wiegandt.librehousehold.household.MemberRemoved;
import eu.wiegandt.librehousehold.notifications.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.notifications.realtime.model.InvalidationSignal;
import eu.wiegandt.librehousehold.notifications.realtime.model.SessionTerminatedSignal;
import eu.wiegandt.librehousehold.notifications.realtime.model.SignalEntity;
import eu.wiegandt.librehousehold.notifications.realtime.model.SignalMessage;
import eu.wiegandt.librehousehold.notifications.realtime.model.SignalType;
import eu.wiegandt.librehousehold.notifications.realtime.model.StreamConnection;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

/**
 * Ends streams whose membership or session ended (RT3). Removing a member and deleting a household
 * only expire the sessions ({@code expireNow()}), which no event reports; without this listener the
 * streams would stay open until the next heartbeat and end without telling the client why.
 *
 * <p>Synchronous {@code AFTER_COMMIT} listeners without an entry in the Event Publication Registry
 * (ADR-019): a signal is only sent once the change is committed, and a lost signal needs no
 * redelivery, since the client resyncs on reconnect.
 */
@Component
class StreamLifecycleListener {

    private final HouseholdEventStreams streams;
    private final SignalSender sender;

    StreamLifecycleListener(HouseholdEventStreams streams, SignalSender sender) {
        this.streams = streams;
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void closeStreamsOfRemovedMember(MemberRemoved event) {
        sender.sendAndClose(List.of(sessionTerminated(Instant.now())), streams.connectionsOfMember(event.memberId()),
                CloseReason.MEMBER_REMOVED);
    }

    // The order is fixed by api/asyncapi.yml: the client learns why before its session is gone
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void closeStreamsOfDeletedHousehold(HouseholdDeleted event) {
        var occurredAt = Instant.now();
        var householdDeleted = new SignalMessage(SignalType.HOUSEHOLD_DELETED,
                new InvalidationSignal(SignalEntity.HOUSEHOLD, event.householdId(), occurredAt));
        sender.sendAndClose(List.of(householdDeleted, sessionTerminated(occurredAt)),
                streams.connectionsOf(event.householdId()), CloseReason.HOUSEHOLD_DELETED);
    }

    /**
     * Logout and session timeout end the stream without an event: the client notices the end
     * through its next request ({@code GET /me}), like after a heartbeat found the session ended.
     */
    @EventListener
    void closeStreamsOfEndedSession(HttpSessionDestroyedEvent event) {
        streams.connectionsOfSession(event.getId()).forEach(this::closeWithoutEvent);
    }

    private void closeWithoutEvent(StreamConnection connection) {
        streams.remove(connection, CloseReason.SESSION_ENDED);
        connection.emitter().complete();
    }

    private static SignalMessage sessionTerminated(Instant occurredAt) {
        return new SignalMessage(SignalType.SESSION_TERMINATED, new SessionTerminatedSignal(occurredAt));
    }
}
