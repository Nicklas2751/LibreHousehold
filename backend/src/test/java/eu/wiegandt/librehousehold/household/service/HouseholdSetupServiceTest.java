package eu.wiegandt.librehousehold.household.service;
import eu.wiegandt.librehousehold.household.AccountRegistered;
import eu.wiegandt.librehousehold.household.ExternalIdentity;
import eu.wiegandt.librehousehold.household.LocalLoginProperties;
import eu.wiegandt.librehousehold.household.PendingSocialRegistrationStore;
import eu.wiegandt.librehousehold.household.exception.*;
import eu.wiegandt.librehousehold.household.mapper.*;
import eu.wiegandt.librehousehold.household.model.*;
import eu.wiegandt.librehousehold.household.repository.*;

import eu.wiegandt.librehousehold.model.HouseholdSetup;
import eu.wiegandt.librehousehold.model.LocalRegistration;
import eu.wiegandt.librehousehold.model.Member;
import org.instancio.Instancio;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class HouseholdSetupServiceTest {

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private InviteRepository inviteRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private AccountSessionAuthenticator accountSessionAuthenticator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PendingSocialRegistrationStore pendingSocialRegistrationStore;

    @Spy
    private LocalLoginProperties localLoginProperties = new LocalLoginProperties(true);

    @Spy
    private HouseholdSetupMapper householdSetupMapper = Mappers.getMapper(HouseholdSetupMapper.class);

    @Spy
    private MemberMapper memberMapper = Mappers.getMapper(MemberMapper.class);

    @InjectMocks
    private HouseholdSetupService service;

    @Nested
    class setupHousehold {

        @Test
        void dataIntegrityViolationOnHouseholdSave_throwsHouseholdAlreadyExistsException() {
            // given
            doThrow(DataIntegrityViolationException.class).when(householdRepository).save(any(HouseholdEntity.class));

            // when / then
            assertThatThrownBy(() -> service.setupHousehold(buildSetup()))
                    .isInstanceOf(HouseholdAlreadyExistsException.class);
        }

        @Test
        void dataIntegrityViolationOnMemberSave_throwsMemberAlreadyExistsException() {
            // given
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doThrow(DataIntegrityViolationException.class).when(memberRepository).save(any(MemberEntity.class));

            // when / then
            assertThatThrownBy(() -> service.setupHousehold(buildSetup()))
                    .isInstanceOf(MemberAlreadyExistsException.class);
        }

        @Test
        void validSetup_savesMemberWithHouseholdIdFromSavedHouseholdAndIsAdminTrue() {
            // given
            var savedHousehold = Instancio.create(HouseholdEntity.class);
            doReturn(savedHousehold).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(Instancio.create(MemberEntity.class)).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));

            // when
            service.setupHousehold(buildSetup());

            // then
            verify(memberRepository).save(argThat(e ->
                    e.householdId().equals(savedHousehold.id()) && e.isAdmin()
            ));
        }

        @Test
        void validSetup_savesHouseholdBeforeMember() {
            // given
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(Instancio.create(MemberEntity.class)).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));

            // when
            service.setupHousehold(buildSetup());

            // then
            var order = inOrder(householdRepository, memberRepository);
            order.verify(householdRepository).save(any(HouseholdEntity.class));
            order.verify(memberRepository).save(any(MemberEntity.class));
        }

        @Test
        void validSetup_inviteTokenIsPersisted() {
            // given
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(Instancio.create(MemberEntity.class)).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));

            // when
            service.setupHousehold(buildSetup());

            // then
            verify(inviteRepository).save(argThat(invite -> invite.token() != null));
        }

        @Test
        void validSetup_inviteLinkedToSavedHousehold() {
            // given
            var savedHousehold = Instancio.create(HouseholdEntity.class);
            doReturn(savedHousehold).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(Instancio.create(MemberEntity.class)).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));

            // when
            service.setupHousehold(buildSetup());

            // then
            verify(inviteRepository).save(argThat(invite -> invite.householdId().equals(savedHousehold.id())));
        }

        @Test
        void validSetup_inviteValidForSevenDays() {
            // given
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(Instancio.create(MemberEntity.class)).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));
            var expectedValidUntil = LocalDate.now().plusDays(7);

            // when
            service.setupHousehold(buildSetup());

            // then
            verify(inviteRepository).save(argThat(invite -> invite.validUntil().equals(expectedValidUntil)));
        }

        @Test
        void validSetup_createsAccountForSavedMemberWithProvidedPassword() {
            // given
            var savedMember = Instancio.create(MemberEntity.class);
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(savedMember).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));
            var setup = buildSetup();

            // when
            service.setupHousehold(setup);

            // then
            verify(accountService).createAccount(savedMember.getId(), setup.getLocalRegistration().orElseThrow().getPassword());
        }

        @Test
        void validSetup_authenticatesAndPersistsSessionForCreatedAccount() {
            // given
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(Instancio.create(MemberEntity.class)).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));
            var setup = buildSetup();

            // when
            service.setupHousehold(setup);

            // then
            verify(accountSessionAuthenticator).authenticateAndPersistSession(
                    setup.getMember().getEmail(), setup.getLocalRegistration().orElseThrow().getPassword());
        }

        @Test
        void validSetup_publishesAccountRegisteredEvent() {
            // given
            var savedMember = Instancio.create(MemberEntity.class);
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(savedMember).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));
            var setup = buildSetup();

            // when
            service.setupHousehold(setup);

            // then
            verify(eventPublisher).publishEvent(new AccountRegistered(savedMember.getId(), setup.getMember().getEmail()));
        }

        @Test
        void validSetup_inviteTokenReturnedInResponse() {
            // given
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(Instancio.create(MemberEntity.class)).when(memberRepository).save(any(MemberEntity.class));
            var savedInvite = Instancio.create(InviteEntity.class);
            doReturn(savedInvite).when(inviteRepository).save(any(InviteEntity.class));

            // when
            var result = service.setupHousehold(buildSetup());

            // then
            assertThat(result.getInviteToken()).isEqualTo(savedInvite.token());
        }
    }

    @Nested
    class setupHouseholdViaSocialLogin {

        private static final ExternalIdentity IDENTITY =
                new ExternalIdentity("google", "external-subject", "verified@example.com", "Provider Name");

        @Test
        void noPendingRegistration_throwsNoPendingSocialRegistrationException() {
            // given
            doReturn(Optional.empty()).when(pendingSocialRegistrationStore).find();

            // when / then
            assertThatThrownBy(() -> service.setupHousehold(buildSocialSetup()))
                    .isInstanceOf(NoPendingSocialRegistrationException.class);
        }

        @Test
        void noPendingRegistration_savesNothing() {
            // given
            doReturn(Optional.empty()).when(pendingSocialRegistrationStore).find();

            // when
            catchThrowable(() -> service.setupHousehold(buildSocialSetup()));

            // then
            verifyNoInteractions(householdRepository, memberRepository, accountService);
        }

        @Test
        void pendingRegistration_savesAdminMemberWithIdentityEmailAndRequestedName() {
            // given
            var savedHousehold = Instancio.create(HouseholdEntity.class);
            var setup = buildSocialSetup();
            doReturn(Optional.of(IDENTITY)).when(pendingSocialRegistrationStore).find();
            doReturn(savedHousehold).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(Instancio.create(MemberEntity.class)).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));
            var expected = new MemberEntity(setup.getMember().getId(), setup.getMember().getName(), IDENTITY.email(),
                    setup.getMember().getAvatar().orElse(null), savedHousehold.id(), true);

            // when
            service.setupHousehold(setup);

            // then
            verify(memberRepository).save(expected);
        }

        @Test
        void pendingRegistration_createsFederatedAccountForSavedMember() {
            // given
            var savedMember = Instancio.create(MemberEntity.class);
            doReturn(Optional.of(IDENTITY)).when(pendingSocialRegistrationStore).find();
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(savedMember).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));

            // when
            service.setupHousehold(buildSocialSetup());

            // then
            verify(accountService).createFederatedAccount(savedMember.getId());
        }

        @Test
        void pendingRegistration_linksExternalIdentityToSavedMember() {
            // given
            var savedMember = Instancio.create(MemberEntity.class);
            doReturn(Optional.of(IDENTITY)).when(pendingSocialRegistrationStore).find();
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(savedMember).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));

            // when
            service.setupHousehold(buildSocialSetup());

            // then
            verify(accountService).linkExternalIdentity(savedMember.getId(), IDENTITY.registrationId(), IDENTITY.subject());
        }

        @Test
        void pendingRegistration_consumesPendingRegistration() {
            // given
            doReturn(Optional.of(IDENTITY)).when(pendingSocialRegistrationStore).find();
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(Instancio.create(MemberEntity.class)).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));

            // when
            service.setupHousehold(buildSocialSetup());

            // then
            verify(pendingSocialRegistrationStore).consume();
        }

        @Test
        void pendingRegistration_authenticatesMemberByIdentityEmail() {
            // given
            doReturn(Optional.of(IDENTITY)).when(pendingSocialRegistrationStore).find();
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(Instancio.create(MemberEntity.class)).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));

            // when
            service.setupHousehold(buildSocialSetup());

            // then
            verify(accountSessionAuthenticator).authenticateFederatedAndPersistSession(IDENTITY.email());
        }

        /**
         * AccountRegistered triggers the verification e-mail, but the provider already verified
         * the address.
         */
        @Test
        void pendingRegistration_publishesNoAccountRegisteredEvent() {
            // given
            doReturn(Optional.of(IDENTITY)).when(pendingSocialRegistrationStore).find();
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(Instancio.create(MemberEntity.class)).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));

            // when
            service.setupHousehold(buildSocialSetup());

            // then
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void pendingRegistration_inviteTokenReturnedInResponse() {
            // given
            var savedInvite = Instancio.create(InviteEntity.class);
            doReturn(Optional.of(IDENTITY)).when(pendingSocialRegistrationStore).find();
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(Instancio.create(MemberEntity.class)).when(memberRepository).save(any(MemberEntity.class));
            doReturn(savedInvite).when(inviteRepository).save(any(InviteEntity.class));

            // when
            var result = service.setupHousehold(buildSocialSetup());

            // then
            assertThat(result.getInviteToken()).isEqualTo(savedInvite.token());
        }

        @Test
        void emailAlreadyTaken_throwsMemberAlreadyExistsException() {
            // given
            doReturn(Optional.of(IDENTITY)).when(pendingSocialRegistrationStore).find();
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doThrow(DataIntegrityViolationException.class).when(memberRepository).save(any(MemberEntity.class));

            // when / then
            assertThatThrownBy(() -> service.setupHousehold(buildSocialSetup()))
                    .isInstanceOf(MemberAlreadyExistsException.class);
        }

        @Test
        void identityAlreadyLinked_throwsMemberAlreadyExistsException() {
            // given
            var savedMember = Instancio.create(MemberEntity.class);
            doReturn(Optional.of(IDENTITY)).when(pendingSocialRegistrationStore).find();
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(savedMember).when(memberRepository).save(any(MemberEntity.class));
            doThrow(DataIntegrityViolationException.class).when(accountService)
                    .linkExternalIdentity(savedMember.getId(), IDENTITY.registrationId(), IDENTITY.subject());

            // when / then
            assertThatThrownBy(() -> service.setupHousehold(buildSocialSetup()))
                    .isInstanceOf(MemberAlreadyExistsException.class);
        }

        @Test
        void localRegistrationAlongsidePendingRegistration_createsPasswordAccount() {
            // given — an explicit password in the request wins over a pending social registration
            var savedMember = Instancio.create(MemberEntity.class);
            lenient().doReturn(Optional.of(IDENTITY)).when(pendingSocialRegistrationStore).find();
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(savedMember).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));
            var setup = buildSetup();

            // when
            service.setupHousehold(setup);

            // then
            verify(accountService).createAccount(savedMember.getId(), setup.getLocalRegistration().orElseThrow().getPassword());
        }
    }

    @Nested
    class setupHouseholdWithLocalLoginDisabled {

        @Test
        void localRegistration_throwsLocalLoginDisabledException() {
            // when / then
            assertThatThrownBy(() -> serviceWithLocalLoginDisabled().setupHousehold(buildSetup()))
                    .isInstanceOf(LocalLoginDisabledException.class);
        }

        @Test
        void localRegistration_savesNothing() {
            // when
            catchThrowable(() -> serviceWithLocalLoginDisabled().setupHousehold(buildSetup()));

            // then
            verifyNoInteractions(householdRepository, memberRepository, accountService);
        }

        @Test
        void pendingSocialRegistration_createsFederatedAccount() {
            // given
            var savedMember = Instancio.create(MemberEntity.class);
            doReturn(Optional.of(new ExternalIdentity("google", "external-subject", "verified@example.com", null)))
                    .when(pendingSocialRegistrationStore).find();
            doReturn(Instancio.create(HouseholdEntity.class)).when(householdRepository).save(any(HouseholdEntity.class));
            doReturn(savedMember).when(memberRepository).save(any(MemberEntity.class));
            doReturn(Instancio.create(InviteEntity.class)).when(inviteRepository).save(any(InviteEntity.class));

            // when
            serviceWithLocalLoginDisabled().setupHousehold(buildSocialSetup());

            // then
            verify(accountService).createFederatedAccount(savedMember.getId());
        }

        private HouseholdSetupService serviceWithLocalLoginDisabled() {
            return new HouseholdSetupService(householdRepository, memberRepository, inviteRepository,
                    householdSetupMapper, memberMapper, accountService, accountSessionAuthenticator, eventPublisher,
                    pendingSocialRegistrationStore, new LocalLoginProperties(false));
        }
    }

    private HouseholdSetup buildSocialSetup() {
        return new HouseholdSetup(Instancio.create(eu.wiegandt.librehousehold.model.Household.class),
                Instancio.create(Member.class));
    }

    private HouseholdSetup buildSetup() {
        var member = Instancio.create(Member.class);
        var household = Instancio.create(eu.wiegandt.librehousehold.model.Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        return new HouseholdSetup(household, member).localRegistration(localRegistration);
    }
}
