package eu.wiegandt.librehousehold.household.service;
import eu.wiegandt.librehousehold.household.exception.*;
import eu.wiegandt.librehousehold.household.mapper.*;
import eu.wiegandt.librehousehold.household.model.*;
import eu.wiegandt.librehousehold.household.repository.*;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import eu.wiegandt.librehousehold.model.*;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@ExtendWith(InstancioExtension.class)
class MemberManagementServiceIT {

    @Autowired
    private MemberManagementService memberManagementService;

    @Autowired
    private HouseholdSetupService setupService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InviteRepository inviteRepository;

    @Autowired
    private AccountRepository accountRepository;

    private HouseholdSetupResponse setupResponse;
    private Household existingHousehold;

    @BeforeEach
    void setUp() {
        var member = Instancio.create(Member.class);
        existingHousehold = Instancio.create(Household.class);
        setupResponse = setupService.setupHousehold(new HouseholdSetup(existingHousehold, member));
    }

    @Nested
    class getMembers {

        @Test
        void adminIsOnlyMember_returnsOneAdminMember() {
            // when
            var result = memberManagementService.getMembers(existingHousehold.getId());

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getIsAdmin()).contains(true);
        }

        @Test
        void additionalMemberJoined_returnsBothMembers() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(LocalMemberRegistration.class);
            memberManagementService.joinHouseholdLocal(token, registration);

            // when
            var result = memberManagementService.getMembers(existingHousehold.getId());

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class joinHouseholdLocal {

        @Test
        void validToken_persistsMemberAndAccountWithSameId() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(LocalMemberRegistration.class);

            // when
            var member = memberManagementService.joinHouseholdLocal(token, registration);

            // then
            assertThat(memberRepository.findById(member.getId()))
                    .hasValueSatisfying(m -> {
                        assertThat(m.householdId()).isEqualTo(existingHousehold.getId());
                        assertThat(m.isAdmin()).isFalse();
                    });
            assertThat(accountRepository.findById(member.getId()))
                    .hasValueSatisfying(a -> assertThat(a.email()).isEqualTo(registration.getEmail()));
        }

        @Test
        void expiredToken_throwsInvalidInviteException_doesNotCreateAccount() {
            // given — create an expired invite directly
            var expiredInvite = inviteRepository.save(new InviteEntity(
                    null, existingHousehold.getId(), UUID.randomUUID(), LocalDate.now().minusDays(1)
            ));
            var registration = Instancio.create(LocalMemberRegistration.class);

            // when / then
            assertThatThrownBy(() -> memberManagementService.joinHouseholdLocal(expiredInvite.token(), registration))
                    .isInstanceOf(InvalidInviteException.class);
            assertThat(accountRepository.findByEmail(registration.getEmail())).isEmpty();
        }

        @Test
        void duplicateEmail_propagatesAsDataIntegrityViolation() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(LocalMemberRegistration.class);
            accountRepository.save(new AccountEntity(UUID.randomUUID(), registration.getEmail(), "hash"));

            // when / then
            assertThatThrownBy(() -> memberManagementService.joinHouseholdLocal(token, registration))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class joinHouseholdAuthenticated {

        @Test
        void validRequest_persistsMemberWithGivenAccountId() {
            // given
            var token = setupResponse.getInviteToken();
            var accountId = UUID.randomUUID();

            // when
            memberManagementService.joinHouseholdAuthenticated(accountId, token, "Max Mustermann", null);

            // then
            assertThat(memberRepository.findById(accountId))
                    .hasValueSatisfying(m -> {
                        assertThat(m.householdId()).isEqualTo(existingHousehold.getId());
                        assertThat(m.isAdmin()).isFalse();
                    });
        }

        @Test
        void accountAlreadyMemberOfOtherHousehold_throwsMemberAlreadyExistsException_doesNotAlterExistingMembership() {
            // given
            var firstToken = setupResponse.getInviteToken();
            var accountId = UUID.randomUUID();
            memberManagementService.joinHouseholdAuthenticated(accountId, firstToken, "Max Mustermann", null);
            var otherHousehold = Instancio.create(Household.class);
            var otherAdmin = Instancio.create(Member.class);
            var otherSetupResponse = setupService.setupHousehold(new HouseholdSetup(otherHousehold, otherAdmin));

            // when / then
            assertThatThrownBy(() -> memberManagementService.joinHouseholdAuthenticated(
                    accountId, otherSetupResponse.getInviteToken(), "Max Mustermann", null))
                    .isInstanceOf(MemberAlreadyExistsException.class);
            assertThat(memberRepository.findById(accountId))
                    .hasValueSatisfying(m -> assertThat(m.householdId()).isEqualTo(existingHousehold.getId()));
        }
    }

    @Nested
    class removeMember {

        @Test
        void memberFound_removesFromDatabase() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(LocalMemberRegistration.class);
            var member = memberManagementService.joinHouseholdLocal(token, registration);

            // when
            memberManagementService.removeMember(member.getId());

            // then
            assertThat(memberRepository.existsById(member.getId())).isFalse();
        }

        @Test
        void memberNotFound_throwsMemberNotFoundException() {
            // when / then
            assertThatThrownBy(() -> memberManagementService.removeMember(UUID.randomUUID()))
                    .isInstanceOf(MemberNotFoundException.class);
        }
    }
}
