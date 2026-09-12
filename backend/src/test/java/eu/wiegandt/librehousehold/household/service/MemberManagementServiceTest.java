package eu.wiegandt.librehousehold.household.service;
import eu.wiegandt.librehousehold.household.AccountRegistered;
import eu.wiegandt.librehousehold.household.HouseholdDeleted;
import eu.wiegandt.librehousehold.household.MemberRemoved;
import eu.wiegandt.librehousehold.household.PasswordResetRequested;
import eu.wiegandt.librehousehold.household.VerificationEmailRequested;
import eu.wiegandt.librehousehold.household.exception.EmailNotVerifiedException;
import eu.wiegandt.librehousehold.household.exception.HouseholdAdminCannotBeRemovedException;
import eu.wiegandt.librehousehold.household.exception.InvalidInviteException;
import eu.wiegandt.librehousehold.household.exception.MemberAlreadyExistsException;
import eu.wiegandt.librehousehold.household.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.household.mapper.*;
import eu.wiegandt.librehousehold.household.model.*;
import eu.wiegandt.librehousehold.household.repository.*;

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
import org.mockito.ArgumentCaptor;
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

    @Spy
    MemberRegistrationMapper memberRegistrationMapper = Mappers.getMapper(MemberRegistrationMapper.class);

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AccountService accountService;

    @Mock
    private AccountSessionAuthenticator accountSessionAuthenticator;

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
    class getMemberWithHouseholdId {

        @Test
        void memberNotInHousehold_throwsMemberNotFoundException() {
            // given — covers both "member does not exist" and "member belongs to a different household":
            // findByIdAndHouseholdId returns empty in both cases, which is exactly the point of the fix
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            doReturn(Optional.empty()).when(memberRepository).findByIdAndHouseholdId(memberId, householdId);

            // when / then
            assertThatThrownBy(() -> service.getMember(householdId, memberId))
                    .isInstanceOf(MemberNotFoundException.class);
        }

        @Test
        void memberBelongsToHousehold_returnsMappedMember() {
            // given
            var householdId = UUID.randomUUID();
            var entity = Instancio.of(memberEntityModel)
                    .set(field(MemberEntity::householdId), householdId).create();
            doReturn(Optional.of(entity)).when(memberRepository).findByIdAndHouseholdId(entity.id(), householdId);

            // when
            var result = service.getMember(householdId, entity.id());

            // then
            assertThat(result.getId()).isEqualTo(entity.id());
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

        @Test
        void validToken_createsAccountForSavedMemberWithProvidedPassword() {
            // given
            var token = UUID.randomUUID();
            var registration = Instancio.create(MemberRegistration.class);
            var invite = Instancio.of(InviteEntity.class)
                    .set(field(InviteEntity::validUntil), LocalDate.now().plusDays(3))
                    .create();
            var savedEntity = Instancio.of(memberEntityModel).create();
            doReturn(Optional.of(invite)).when(inviteRepository).findByToken(token);
            doReturn(savedEntity).when(memberRepository).save(any(MemberEntity.class));

            // when
            service.joinHousehold(token, registration);

            // then
            verify(accountService).createAccount(savedEntity.id(), registration.getLocalRegistration().getPassword());
        }

        @Test
        void validToken_publishesAccountRegisteredEvent() {
            // given
            var token = UUID.randomUUID();
            var registration = Instancio.create(MemberRegistration.class);
            var invite = Instancio.of(InviteEntity.class)
                    .set(field(InviteEntity::validUntil), LocalDate.now().plusDays(3))
                    .create();
            var savedEntity = Instancio.of(memberEntityModel).create();
            doReturn(Optional.of(invite)).when(inviteRepository).findByToken(token);
            doReturn(savedEntity).when(memberRepository).save(any(MemberEntity.class));

            // when
            service.joinHousehold(token, registration);

            // then
            verify(eventPublisher).publishEvent(new AccountRegistered(savedEntity.id(), registration.getEmail()));
        }

        @Test
        void validToken_authenticatesAndPersistsSessionForCreatedAccount() {
            // given
            var token = UUID.randomUUID();
            var registration = Instancio.create(MemberRegistration.class);
            var invite = Instancio.of(InviteEntity.class)
                    .set(field(InviteEntity::validUntil), LocalDate.now().plusDays(3))
                    .create();
            var savedEntity = Instancio.of(memberEntityModel).create();
            doReturn(Optional.of(invite)).when(inviteRepository).findByToken(token);
            doReturn(savedEntity).when(memberRepository).save(any(MemberEntity.class));

            // when
            service.joinHousehold(token, registration);

            // then
            verify(accountSessionAuthenticator).authenticateAndPersistSession(
                    registration.getEmail(), registration.getLocalRegistration().getPassword());
        }
    }

    @Nested
    class updateMember {

        @Test
        void memberNotInHousehold_throwsMemberNotFoundExceptionWithoutUpdating() {
            // given — the member exists (updateName would succeed), but not in this household
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var update = new MemberUpdate().name("New Name");
            doReturn(false).when(memberRepository).existsByIdAndHouseholdId(memberId, householdId);

            // when / then
            assertThatThrownBy(() -> service.updateMember(householdId, memberId, update))
                    .isInstanceOf(MemberNotFoundException.class);
            verify(memberRepository, never()).updateName(any(), any());
        }

        @Test
        void duplicateEmail_throwsMemberAlreadyExistsException() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var update = new MemberUpdate().email("taken@example.com");
            doReturn(true).when(memberRepository).existsByIdAndHouseholdId(memberId, householdId);
            doReturn(true).when(accountService).isEmailVerified(memberId);
            doThrow(DataIntegrityViolationException.class).when(memberRepository).updateEmail(memberId, "taken@example.com");

            // when / then
            assertThatThrownBy(() -> service.updateMember(householdId, memberId, update))
                    .isInstanceOf(MemberAlreadyExistsException.class);
        }

        @Test
        void validNameUpdate_updatesNameInRepository() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var update = new MemberUpdate().name("Updated Name");
            doReturn(true).when(memberRepository).existsByIdAndHouseholdId(memberId, householdId);

            // when
            service.updateMember(householdId, memberId, update);

            // then
            verify(memberRepository).updateName(memberId, "Updated Name");
        }

        @Test
        void unverifiedAccountWithOnlyNameChange_succeedsWithoutCheckingVerification() {
            // given — name/avatar remain changeable regardless of verification status
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var update = new MemberUpdate().name("Updated Name");
            doReturn(true).when(memberRepository).existsByIdAndHouseholdId(memberId, householdId);

            // when
            service.updateMember(householdId, memberId, update);

            // then
            verify(accountService, never()).isEmailVerified(any());
        }

        @Test
        void unverifiedAccountWithEmailChange_throwsEmailNotVerifiedExceptionWithoutUpdating() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var update = new MemberUpdate().email("updated@example.com");
            doReturn(true).when(memberRepository).existsByIdAndHouseholdId(memberId, householdId);
            doReturn(false).when(accountService).isEmailVerified(memberId);

            // when / then
            assertThatThrownBy(() -> service.updateMember(householdId, memberId, update))
                    .isInstanceOf(EmailNotVerifiedException.class);
            verify(memberRepository, never()).updateEmail(any(), any());
        }

        @Test
        void validEmailUpdate_updatesEmailInRepository() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var update = new MemberUpdate().email("updated@example.com");
            doReturn(true).when(memberRepository).existsByIdAndHouseholdId(memberId, householdId);
            doReturn(true).when(accountService).isEmailVerified(memberId);

            // when
            service.updateMember(householdId, memberId, update);

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
    class memberExistsById {

        @Test
        void memberNotFound_returnsFalse() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(false).when(memberRepository).existsById(memberId);

            // when
            var result = service.memberExistsById(memberId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void memberFound_returnsTrue() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(true).when(memberRepository).existsById(memberId);

            // when
            var result = service.memberExistsById(memberId);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    class existsByEmail {

        @Test
        void unknownEmail_returnsFalse() {
            // given
            var email = "unknown@example.com";
            doReturn(false).when(memberRepository).existsByEmail(email);

            // when
            var result = service.existsByEmail(email);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void knownEmail_returnsTrue() {
            // given
            var email = "max@example.com";
            doReturn(true).when(memberRepository).existsByEmail(email);

            // when
            var result = service.existsByEmail(email);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    class isEmailAvailable {

        @Test
        void unknownEmail_returnsTrue() {
            // given
            var email = "unknown@example.com";
            doReturn(false).when(memberRepository).existsByEmail(email);

            // when
            var result = service.isEmailAvailable(email);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void knownEmail_returnsFalse() {
            // given
            var email = "max@example.com";
            doReturn(true).when(memberRepository).existsByEmail(email);

            // when
            var result = service.isEmailAvailable(email);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class findMemberIdByEmail {

        @Test
        void unknownEmail_returnsEmptyOptional() {
            // given
            var email = "unknown@example.com";
            doReturn(Optional.empty()).when(memberRepository).findByEmail(email);

            // when
            var result = service.findMemberIdByEmail(email);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void knownEmail_returnsMemberId() {
            // given
            var email = "max@example.com";
            var entity = Instancio.of(memberEntityModel)
                    .set(field(MemberEntity::email), email)
                    .create();
            doReturn(Optional.of(entity)).when(memberRepository).findByEmail(email);

            // when
            var result = service.findMemberIdByEmail(email);

            // then
            assertThat(result).contains(entity.id());
        }
    }

    @Nested
    class isAdmin {

        @Test
        void memberNotFound_returnsFalse() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(Optional.empty()).when(memberRepository).findById(memberId);

            // when
            var result = service.isAdmin(memberId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void memberFoundAndNotAdmin_returnsFalse() {
            // given
            var memberId = UUID.randomUUID();
            var entity = Instancio.of(memberEntityModel)
                    .set(field(MemberEntity::isAdmin), false)
                    .create();
            doReturn(Optional.of(entity)).when(memberRepository).findById(memberId);

            // when
            var result = service.isAdmin(memberId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void memberFoundAndIsAdmin_returnsTrue() {
            // given
            var memberId = UUID.randomUUID();
            var entity = Instancio.of(memberEntityModel)
                    .set(field(MemberEntity::isAdmin), true)
                    .create();
            doReturn(Optional.of(entity)).when(memberRepository).findById(memberId);

            // when
            var result = service.isAdmin(memberId);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    class leaveHousehold {

        @Test
        void memberNotFound_throwsMemberNotFoundException() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(Optional.empty()).when(memberRepository).findById(memberId);

            // when / then
            assertThatThrownBy(() -> service.leaveHousehold(memberId))
                    .isInstanceOf(MemberNotFoundException.class);
        }

        @Test
        void memberIsAdmin_throwsHouseholdAdminCannotBeRemovedExceptionWithoutDeleting() {
            // given
            var entity = Instancio.of(memberEntityModel)
                    .set(field(MemberEntity::isAdmin), true)
                    .create();
            doReturn(Optional.of(entity)).when(memberRepository).findById(entity.id());

            // when / then
            assertThatThrownBy(() -> service.leaveHousehold(entity.id()))
                    .isInstanceOf(HouseholdAdminCannotBeRemovedException.class);
            verify(memberRepository, never()).deleteById(any());
        }

        @Test
        void memberFound_deletesMemberById() {
            // given
            var entity = Instancio.of(memberEntityModel)
                    .set(field(MemberEntity::isAdmin), false)
                    .create();
            doReturn(Optional.of(entity)).when(memberRepository).findById(entity.id());

            // when
            service.leaveHousehold(entity.id());

            // then
            verify(memberRepository).deleteById(entity.id());
        }

        @Test
        void validCall_publishesMemberRemovedWithMemberAndHouseholdData() {
            // given
            var householdName = "Musterfamilie";
            var entity = Instancio.of(memberEntityModel)
                    .set(field(MemberEntity::isAdmin), false)
                    .create();
            doReturn(Optional.of(entity)).when(memberRepository).findById(entity.id());
            doReturn(Optional.of(householdName)).when(householdRepository).findNameById(entity.householdId());
            var expectedEvent = new MemberRemoved(entity.id(), entity.name(), entity.email(), householdName);
            var eventCaptor = ArgumentCaptor.forClass(MemberRemoved.class);

            // when
            service.leaveHousehold(entity.id());

            // then
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedEvent);
        }
    }

    @Nested
    class removeMember {

        @Test
        void memberBelongsToDifferentHousehold_throwsMemberNotFoundExceptionWithoutDeleting() {
            // given — the member exists (deletion would succeed), but not in this household
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            doReturn(Optional.empty()).when(memberRepository).findByIdAndHouseholdId(memberId, householdId);

            // when / then
            assertThatThrownBy(() -> service.removeMember(householdId, memberId))
                    .isInstanceOf(MemberNotFoundException.class);
            verify(memberRepository, never()).deleteById(any());
        }

        @Test
        void memberIsAdmin_throwsHouseholdAdminCannotBeRemovedExceptionWithoutDeleting() {
            // given
            var householdId = UUID.randomUUID();
            var entity = Instancio.of(memberEntityModel)
                    .set(field(MemberEntity::householdId), householdId)
                    .set(field(MemberEntity::isAdmin), true)
                    .create();
            doReturn(Optional.of(entity)).when(memberRepository).findByIdAndHouseholdId(entity.id(), householdId);

            // when / then
            assertThatThrownBy(() -> service.removeMember(householdId, entity.id()))
                    .isInstanceOf(HouseholdAdminCannotBeRemovedException.class);
            verify(memberRepository, never()).deleteById(any());
        }

        @Test
        void memberBelongsToHousehold_deletesMemberById() {
            // given
            var householdId = UUID.randomUUID();
            var entity = Instancio.of(memberEntityModel)
                    .set(field(MemberEntity::householdId), householdId)
                    .set(field(MemberEntity::isAdmin), false)
                    .create();
            doReturn(Optional.of(entity)).when(memberRepository).findByIdAndHouseholdId(entity.id(), householdId);

            // when
            service.removeMember(householdId, entity.id());

            // then
            verify(memberRepository).deleteById(entity.id());
        }

        @Test
        void validCall_publishesMemberRemovedWithMemberAndHouseholdData() {
            // given
            var householdId = UUID.randomUUID();
            var householdName = "Musterfamilie";
            var entity = Instancio.of(memberEntityModel)
                    .set(field(MemberEntity::householdId), householdId)
                    .set(field(MemberEntity::isAdmin), false)
                    .create();
            doReturn(Optional.of(entity)).when(memberRepository).findByIdAndHouseholdId(entity.id(), householdId);
            doReturn(Optional.of(householdName)).when(householdRepository).findNameById(householdId);
            var expectedEvent = new MemberRemoved(entity.id(), entity.name(), entity.email(), householdName);
            var eventCaptor = ArgumentCaptor.forClass(MemberRemoved.class);

            // when
            service.removeMember(householdId, entity.id());

            // then
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedEvent);
        }
    }

    @Nested
    class resendVerificationEmail {

        @Test
        void memberNotFound_throwsMemberNotFoundException() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(Optional.empty()).when(memberRepository).findById(memberId);

            // when / then
            assertThatThrownBy(() -> service.resendVerificationEmail(memberId))
                    .isInstanceOf(MemberNotFoundException.class);
        }

        @Test
        void memberFound_publishesVerificationEmailRequestedEvent() {
            // given
            var entity = Instancio.of(memberEntityModel).create();
            doReturn(Optional.of(entity)).when(memberRepository).findById(entity.id());

            // when
            service.resendVerificationEmail(entity.id());

            // then
            verify(eventPublisher).publishEvent(new VerificationEmailRequested(entity.id(), entity.email()));
        }
    }

    @Nested
    class isEmailVerified {

        @Test
        void unverifiedAccount_returnsFalse() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(false).when(accountService).isEmailVerified(memberId);

            // when
            var result = service.isEmailVerified(memberId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void verifiedAccount_returnsTrue() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(true).when(accountService).isEmailVerified(memberId);

            // when
            var result = service.isEmailVerified(memberId);

            // then
            assertThat(result).isTrue();
        }
    }

    @Nested
    class requestPasswordReset {

        @Test
        void existingEmail_publishesPasswordResetRequestedEvent() {
            // given
            var email = "max@example.com";
            var entity = Instancio.of(memberEntityModel)
                    .set(field(MemberEntity::email), email)
                    .create();
            doReturn(Optional.of(entity)).when(memberRepository).findByEmail(email);

            // when
            service.requestPasswordReset(email);

            // then
            verify(eventPublisher).publishEvent(new PasswordResetRequested(entity.id(), email));
        }

        @Test
        void unknownEmail_publishesNothing() {
            // given
            var email = "unknown@example.com";
            doReturn(Optional.empty()).when(memberRepository).findByEmail(email);

            // when
            service.requestPasswordReset(email);

            // then
            verifyNoInteractions(eventPublisher);
        }
    }
}
