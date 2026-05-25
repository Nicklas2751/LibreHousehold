package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.HouseholdUpdate;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class HouseholdManagementServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private InviteRepository inviteRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private HouseholdManagementService service;

    @Nested
    class updateName {

        @Test
        void householdNotFound_throwsHouseholdNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(0).when(householdRepository).updateName(householdId, "New Name");

            // when / then
            assertThatThrownBy(() -> service.updateName(householdId, new HouseholdUpdate("New Name")))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void householdFound_updatesNameInRepository() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(1).when(householdRepository).updateName(householdId, "New Name");

            // when
            service.updateName(householdId, new HouseholdUpdate("New Name"));

            // then
            verify(householdRepository).updateName(householdId, "New Name");
        }
    }

    @Nested
    class deleteHousehold {

        @Test
        void householdNotFound_throwsHouseholdNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(0).when(householdRepository).deleteHouseholdById(householdId);

            // when / then
            assertThatThrownBy(() -> service.deleteHousehold(householdId))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void householdFound_deletesInvitesBeforeHousehold() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(1).when(householdRepository).deleteHouseholdById(householdId);

            // when
            service.deleteHousehold(householdId);

            // then
            var order = inOrder(inviteRepository, householdRepository);
            order.verify(inviteRepository).deleteByHouseholdId(householdId);
            order.verify(householdRepository).deleteHouseholdById(householdId);
        }

        @Test
        void householdFound_publishesHouseholdDeletedEvent() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(1).when(householdRepository).deleteHouseholdById(householdId);

            // when
            service.deleteHousehold(householdId);

            // then
            verify(eventPublisher).publishEvent(new HouseholdDeleted(householdId));
        }
    }

    @Nested
    class getInvite {

        @Test
        void noInviteExists_throwsHouseholdNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(Optional.empty()).when(inviteRepository).findByHouseholdId(householdId);

            // when / then
            assertThatThrownBy(() -> service.getInvite(householdId))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void inviteExists_returnsTokenAndValidUntil() {
            // given
            var householdId = UUID.randomUUID();
            var invite = Instancio.of(InviteEntity.class)
                    .set(field(InviteEntity::householdId), householdId)
                    .create();
            doReturn(Optional.of(invite)).when(inviteRepository).findByHouseholdId(householdId);

            // when
            var result = service.getInvite(householdId);

            // then
            assertThat(result.getInviteToken()).isEqualTo(invite.token());
            assertThat(result.getInviteValidUntil()).isEqualTo(invite.validUntil());
        }
    }

    @Nested
    class regenerateInvite {

        @Test
        void householdNotFound_throwsHouseholdNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            doThrow(DataIntegrityViolationException.class).when(inviteRepository).save(any(InviteEntity.class));

            // when / then
            assertThatThrownBy(() -> service.regenerateInvite(householdId))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void householdFound_deletesOldInviteFirst() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));

            // when
            service.regenerateInvite(householdId);

            // then
            var order = inOrder(inviteRepository);
            order.verify(inviteRepository).deleteByHouseholdId(householdId);
            order.verify(inviteRepository).save(any(InviteEntity.class));
        }

        @Test
        void householdFound_newTokenIsLinkedToHousehold() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));

            // when
            service.regenerateInvite(householdId);

            // then
            verify(inviteRepository).save(argThat(invite -> invite.householdId().equals(householdId)));
        }

        @Test
        void householdFound_newTokenIsValidForSevenDays() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));
            var expectedValidUntil = LocalDate.now().plusDays(7);

            // when
            service.regenerateInvite(householdId);

            // then
            verify(inviteRepository).save(argThat(invite -> invite.validUntil().equals(expectedValidUntil)));
        }

        @Test
        void householdFound_returnsNewTokenFromSavedInvite() {
            // given
            var householdId = UUID.randomUUID();
            var savedInvite = Instancio.create(InviteEntity.class);
            doReturn(savedInvite).when(inviteRepository).save(any(InviteEntity.class));

            // when
            var result = service.regenerateInvite(householdId);

            // then
            assertThat(result.getInviteToken()).isEqualTo(savedInvite.token());
            assertThat(result.getInviteValidUntil()).isEqualTo(savedInvite.validUntil());
        }
    }

    @Nested
    class transferOwnership {

        @Test
        void householdNotFound_throwsHouseholdNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            var newAdminId = UUID.randomUUID();
            doReturn(0).when(householdRepository).updateAdminId(householdId, newAdminId);

            // when / then
            assertThatThrownBy(() -> service.transferOwnership(householdId, newAdminId))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void householdFound_updatesAdminIdInRepository() {
            // given
            var householdId = UUID.randomUUID();
            var newAdminId = UUID.randomUUID();
            doReturn(1).when(householdRepository).updateAdminId(householdId, newAdminId);

            // when
            service.transferOwnership(householdId, newAdminId);

            // then
            verify(householdRepository).updateAdminId(householdId, newAdminId);
        }
    }
}
