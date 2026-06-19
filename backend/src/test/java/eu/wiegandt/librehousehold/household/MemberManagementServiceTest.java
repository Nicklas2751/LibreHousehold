package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.Member;
import eu.wiegandt.librehousehold.model.MemberRegistration;
import eu.wiegandt.librehousehold.model.MemberUpdate;
import org.instancio.Instancio;
import org.instancio.Model;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class MemberManagementServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private InviteRepository inviteRepository;

    @Spy
    MemberMapper memberMapper = Mappers.getMapper(MemberMapper.class);

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MemberManagementService service;

    private final Model<MemberEntity> memberEntityModel = Instancio.of(MemberEntity.class).toModel();

    @Nested
    class getMembers {

        @Test
        void noMembers_returnsEmptyList() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(List.of()).when(memberRepository).findByHouseholdId(householdId);

            // when
            var result = service.getMembers(householdId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void multipleMembers_returnsMappedMembers() {
            // given
            var householdId = UUID.randomUUID();
            var entities = Instancio.ofList(memberEntityModel)
                    .set(field(MemberEntity::householdId), householdId).create();
            doReturn(entities).when(memberRepository).findByHouseholdId(householdId);

            // when
            var result = service.getMembers(householdId);

            // then
            assertThat(result).extracting(Member::getId)
                    .containsExactlyInAnyOrderElementsOf(entities.stream().map(MemberEntity::id).toList());
        }
    }

    @Nested
    class getMember {

        @Test
        void memberNotFound_throwsMemberNotFoundException() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(Optional.empty()).when(memberRepository).findById(memberId);

            // when / then
            assertThatThrownBy(() -> service.getMember(memberId))
                    .isInstanceOf(MemberNotFoundException.class);
        }

        @Test
        void memberFound_returnsMappedMember() {
            // given
            var memberId = UUID.randomUUID();
            var entity = Instancio.of(memberEntityModel).create();
            doReturn(Optional.of(entity)).when(memberRepository).findById(memberId);

            // when
            var result = service.getMember(memberId);

            // then
            assertThat(result.getId()).isEqualTo(entity.id());
            assertThat(result.getName()).isEqualTo(entity.name());
        }
    }

    @Nested
    class resolveInvite {

        @Test
        void tokenNotFound_throwsInvalidInviteException() {
            // given
            var token = UUID.randomUUID();
            doReturn(Optional.empty()).when(inviteRepository).findByToken(token);

            // when / then
            assertThatThrownBy(() -> service.resolveInvite(token))
                    .isInstanceOf(InvalidInviteException.class);
        }

        @Test
        void tokenExpired_throwsInvalidInviteException() {
            // given
            var token = UUID.randomUUID();
            var expiredInvite = Instancio.of(InviteEntity.class)
                    .set(field(InviteEntity::validUntil), LocalDate.now().minusDays(1))
                    .create();
            doReturn(Optional.of(expiredInvite)).when(inviteRepository).findByToken(token);

            // when / then
            assertThatThrownBy(() -> service.resolveInvite(token))
                    .isInstanceOf(InvalidInviteException.class);
        }

        @Test
        void validToken_returnsInviteInfoWithHouseholdName() {
            // given
            var token = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var householdName = "Test Haushalt";
            var invite = Instancio.of(InviteEntity.class)
                    .set(field(InviteEntity::token), token)
                    .set(field(InviteEntity::householdId), householdId)
                    .set(field(InviteEntity::validUntil), LocalDate.now().plusDays(3))
                    .create();
            doReturn(Optional.of(invite)).when(inviteRepository).findByToken(token);
            doReturn(Optional.of(householdName)).when(householdRepository).findNameById(householdId);

            // when
            var result = service.resolveInvite(token);

            // then
            assertThat(result.getHouseholdId()).isEqualTo(householdId);
            assertThat(result.getHouseholdName()).isEqualTo(householdName);
            assertThat(result.getValidUntil()).isEqualTo(invite.validUntil());
        }
    }

    @Nested
    class joinHousehold {

        @Test
        void tokenNotFound_throwsInvalidInviteException() {
            // given
            var token = UUID.randomUUID();
            var registration = Instancio.create(MemberRegistration.class);
            doReturn(Optional.empty()).when(inviteRepository).findByToken(token);

            // when / then
            assertThatThrownBy(() -> service.joinHousehold(token, registration))
                    .isInstanceOf(InvalidInviteException.class);
        }

        @Test
        void tokenExpired_throwsInvalidInviteException() {
            // given
            var token = UUID.randomUUID();
            var registration = Instancio.create(MemberRegistration.class);
            var expiredInvite = Instancio.of(InviteEntity.class)
                    .set(field(InviteEntity::validUntil), LocalDate.now().minusDays(1))
                    .create();
            doReturn(Optional.of(expiredInvite)).when(inviteRepository).findByToken(token);

            // when / then
            assertThatThrownBy(() -> service.joinHousehold(token, registration))
                    .isInstanceOf(InvalidInviteException.class);
        }

        @Test
        void duplicateEmail_throwsMemberAlreadyExistsException() {
            // given
            var token = UUID.randomUUID();
            var registration = Instancio.create(MemberRegistration.class);
            var invite = Instancio.of(InviteEntity.class)
                    .set(field(InviteEntity::validUntil), LocalDate.now().plusDays(3))
                    .create();
            doReturn(Optional.of(invite)).when(inviteRepository).findByToken(token);
            doThrow(DataIntegrityViolationException.class).when(memberRepository).save(any(MemberEntity.class));

            // when / then
            assertThatThrownBy(() -> service.joinHousehold(token, registration))
                    .isInstanceOf(MemberAlreadyExistsException.class);
        }

        @Test
        void validToken_savesWithHouseholdIdFromTokenAndIsAdminFalse() {
            // given
            var token = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var registration = Instancio.create(MemberRegistration.class);
            var invite = Instancio.of(InviteEntity.class)
                    .set(field(InviteEntity::householdId), householdId)
                    .set(field(InviteEntity::validUntil), LocalDate.now().plusDays(3))
                    .create();
            var savedEntity = Instancio.of(memberEntityModel).create();
            doReturn(Optional.of(invite)).when(inviteRepository).findByToken(token);
            doReturn(savedEntity).when(memberRepository).save(any(MemberEntity.class));

            // when
            service.joinHousehold(token, registration);

            // then
            verify(memberRepository).save(argThat(e ->
                    e.householdId().equals(householdId) && !e.isAdmin()
            ));
        }
    }

    @Nested
    class updateMember {

        @Test
        void nameUpdateWithZeroRows_throwsMemberNotFoundException() {
            // given
            var memberId = UUID.randomUUID();
            var update = new MemberUpdate().name("New Name");
            doReturn(0).when(memberRepository).updateName(memberId, "New Name");

            // when / then
            assertThatThrownBy(() -> service.updateMember(memberId, update))
                    .isInstanceOf(MemberNotFoundException.class);
        }

        @Test
        void emailUpdateWithZeroRows_throwsMemberNotFoundException() {
            // given
            var memberId = UUID.randomUUID();
            var update = new MemberUpdate().email("new@example.com");
            doReturn(0).when(memberRepository).updateEmail(memberId, "new@example.com");

            // when / then
            assertThatThrownBy(() -> service.updateMember(memberId, update))
                    .isInstanceOf(MemberNotFoundException.class);
        }

        @Test
        void duplicateEmail_throwsMemberAlreadyExistsException() {
            // given
            var memberId = UUID.randomUUID();
            var update = new MemberUpdate().email("taken@example.com");
            doThrow(DataIntegrityViolationException.class).when(memberRepository).updateEmail(memberId, "taken@example.com");

            // when / then
            assertThatThrownBy(() -> service.updateMember(memberId, update))
                    .isInstanceOf(MemberAlreadyExistsException.class);
        }

        @Test
        void validNameUpdate_updatesNameInRepository() {
            // given
            var memberId = UUID.randomUUID();
            var update = new MemberUpdate().name("Updated Name");
            doReturn(1).when(memberRepository).updateName(memberId, "Updated Name");

            // when
            service.updateMember(memberId, update);

            // then
            verify(memberRepository).updateName(memberId, "Updated Name");
        }

        @Test
        void validEmailUpdate_updatesEmailInRepository() {
            // given
            var memberId = UUID.randomUUID();
            var update = new MemberUpdate().email("updated@example.com");
            doReturn(1).when(memberRepository).updateEmail(memberId, "updated@example.com");

            // when
            service.updateMember(memberId, update);

            // then
            verify(memberRepository).updateEmail(memberId, "updated@example.com");
        }
    }

    @Nested
    class findMemberIdsByHouseholdId {

        @Test
        void existingMembers_returnsAllMemberIds() {
            // given
            var householdId = UUID.randomUUID();
            var entities = Instancio.ofList(memberEntityModel)
                    .set(field(MemberEntity::householdId), householdId).create();
            doReturn(entities).when(memberRepository).findByHouseholdId(householdId);

            // when
            var result = service.findMemberIdsByHouseholdId(householdId);

            // then
            assertThat(result).containsExactlyInAnyOrderElementsOf(
                    entities.stream().map(MemberEntity::id).toList());
        }

        @Test
        void noMembers_returnsEmptyList() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(List.of()).when(memberRepository).findByHouseholdId(householdId);

            // when
            var result = service.findMemberIdsByHouseholdId(householdId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class findMemberNamesByIds {

        @Test
        void emptyCollection_returnsEmptyMapWithoutQueryingRepository() {
            // given / when
            var result = service.findMemberNamesByIds(List.of());

            // then
            assertThat(result).isEmpty();
            verify(memberRepository, never()).findNamesByIds(any());
        }

        @Test
        void knownIds_returnsNameMap() {
            // given
            var id1 = UUID.randomUUID();
            var id2 = UUID.randomUUID();
            var projection1 = new MemberNameProjection(id1, "Alice");
            var projection2 = new MemberNameProjection(id2, "Bob");
            doReturn(List.of(projection1, projection2)).when(memberRepository).findNamesByIds(any());

            // when
            var result = service.findMemberNamesByIds(Set.of(id1, id2));

            // then
            assertThat(result).isEqualTo(Map.of(id1, "Alice", id2, "Bob"));
        }
    }

    @Nested
    class removeMember {

        @Test
        void memberNotFound_throwsMemberNotFoundException() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(false).when(memberRepository).existsById(memberId);

            // when / then
            assertThatThrownBy(() -> service.removeMember(memberId))
                    .isInstanceOf(MemberNotFoundException.class);
        }

        @Test
        void memberFound_publishesMemberRemovedEvent() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(true).when(memberRepository).existsById(memberId);

            // when
            service.removeMember(memberId);

            // then
            verify(eventPublisher).publishEvent(new MemberRemoved(memberId));
        }

        @Test
        void memberFound_deletesMemberById() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(true).when(memberRepository).existsById(memberId);

            // when
            service.removeMember(memberId);

            // then
            verify(memberRepository).deleteById(memberId);
        }
    }
}
