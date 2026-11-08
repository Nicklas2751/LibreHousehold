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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class StreamLifecycleListenerTest {

    // Instant.now() of the listener; the tests compare everything else of the payloads
    private static final String OCCURRED_AT = "payload.occurredAt";
    private static final String HOUSEHOLD_NAME = "Musterhaushalt";
    private static final String SESSION_ID = "session";

    @Mock
    private HouseholdEventStreams streams;

    @Mock
    private SignalSender sender;

    @InjectMocks
    private StreamLifecycleListener listener;

    @Captor
    private ArgumentCaptor<List<SignalMessage>> messages;

    @Nested
    class closeStreamsOfRemovedMember {

        @Test
        void removedMember_sessionTerminatedSent() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(Set.of(connection(UUID.randomUUID(), memberId))).when(streams).connectionsOfMember(memberId);
            var expected = List.of(new SignalMessage(SignalType.SESSION_TERMINATED, new SessionTerminatedSignal(null)));

            // when
            listener.closeStreamsOfRemovedMember(
                    new MemberRemoved(memberId, "Anna", "anna@example.com", HOUSEHOLD_NAME));

            // then
            verify(sender).sendAndClose(messages.capture(), any(), any());
            assertThat(messages.getValue()).usingRecursiveComparison().ignoringFields(OCCURRED_AT).isEqualTo(expected);
        }

        @Test
        void removedMember_connectionsOfMemberClosedWithReasonMemberRemoved() {
            // given
            var memberId = UUID.randomUUID();
            var connectionsOfMember = Set.of(connection(UUID.randomUUID(), memberId),
                    connection(UUID.randomUUID(), memberId));
            doReturn(connectionsOfMember).when(streams).connectionsOfMember(memberId);

            // when
            listener.closeStreamsOfRemovedMember(
                    new MemberRemoved(memberId, "Anna", "anna@example.com", HOUSEHOLD_NAME));

            // then
            verify(sender).sendAndClose(anyList(), eq(connectionsOfMember),
                    eq(CloseReason.MEMBER_REMOVED));
        }
    }

    @Nested
    class closeStreamsOfDeletedHousehold {

        // The order is fixed by api/asyncapi.yml; SignalSenderTest covers that it survives the thread pool
        @Test
        void deletedHousehold_householdDeletedThenSessionTerminatedSent() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(Set.of(connection(householdId, UUID.randomUUID()))).when(streams).connectionsOf(householdId);
            var expected = List.of(
                    new SignalMessage(SignalType.HOUSEHOLD_DELETED,
                            new InvalidationSignal(SignalEntity.HOUSEHOLD, householdId, null)),
                    new SignalMessage(SignalType.SESSION_TERMINATED, new SessionTerminatedSignal(null)));

            // when
            listener.closeStreamsOfDeletedHousehold(new HouseholdDeleted(householdId, HOUSEHOLD_NAME, List.of()));

            // then
            verify(sender).sendAndClose(messages.capture(), any(), any());
            assertThat(messages.getValue()).usingRecursiveComparison().ignoringFields(OCCURRED_AT).isEqualTo(expected);
        }

        @Test
        void deletedHousehold_connectionsOfHouseholdClosedWithReasonHouseholdDeleted() {
            // given
            var householdId = UUID.randomUUID();
            var connectionsOfHousehold = Set.of(connection(householdId, UUID.randomUUID()),
                    connection(householdId, UUID.randomUUID()));
            doReturn(connectionsOfHousehold).when(streams).connectionsOf(householdId);

            // when
            listener.closeStreamsOfDeletedHousehold(new HouseholdDeleted(householdId, HOUSEHOLD_NAME, List.of()));

            // then
            verify(sender).sendAndClose(anyList(), eq(connectionsOfHousehold),
                    eq(CloseReason.HOUSEHOLD_DELETED));
        }
    }

    @Nested
    class closeStreamsOfEndedSession {

        @Test
        void connectionOfSession_removedFromRegistryWithReasonSessionEnded() {
            // given
            var connection = connection(UUID.randomUUID(), UUID.randomUUID());
            doReturn(Set.of(connection)).when(streams).connectionsOfSession(SESSION_ID);

            // when
            listener.closeStreamsOfEndedSession(new HttpSessionDestroyedEvent(new MockHttpSession(null, SESSION_ID)));

            // then
            verify(streams).remove(connection, CloseReason.SESSION_ENDED);
        }

        @Test
        void connectionOfSession_completedWithoutEvent() {
            // given
            var emitter = mock(SseEmitter.class);
            doReturn(Set.of(new StreamConnection(UUID.randomUUID(), UUID.randomUUID(), SESSION_ID, emitter)))
                    .when(streams).connectionsOfSession(SESSION_ID);

            // when
            listener.closeStreamsOfEndedSession(new HttpSessionDestroyedEvent(new MockHttpSession(null, SESSION_ID)));

            // then
            verify(emitter).complete();
            verifyNoMoreInteractions(emitter);
        }
    }

    private StreamConnection connection(UUID householdId, UUID memberId) {
        return new StreamConnection(householdId, memberId, SESSION_ID, mock(SseEmitter.class));
    }
}
