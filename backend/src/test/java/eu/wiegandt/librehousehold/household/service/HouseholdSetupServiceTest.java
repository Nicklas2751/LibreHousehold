package eu.wiegandt.librehousehold.household.service;
import eu.wiegandt.librehousehold.household.exception.*;
import eu.wiegandt.librehousehold.household.mapper.*;
import eu.wiegandt.librehousehold.household.model.*;
import eu.wiegandt.librehousehold.household.repository.*;

import eu.wiegandt.librehousehold.model.HouseholdSetup;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Spy
    private HouseholdSetupMapper householdSetupMapper = Mappers.getMapper(HouseholdSetupMapper.class);

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

    private HouseholdSetup buildSetup() {
        var member = Instancio.create(Member.class);
        var household = Instancio.create(eu.wiegandt.librehousehold.model.Household.class);
        return new HouseholdSetup(household, member);
    }
}
