package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.exception.HouseholdAdminCannotBeRemovedException;
import eu.wiegandt.librehousehold.household.exception.InvalidInviteException;
import eu.wiegandt.librehousehold.household.exception.MemberAlreadyExistsException;
import eu.wiegandt.librehousehold.household.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.household.model.InviteEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.model.*;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;

/**
 * {@code webEnvironment = MOCK} (not {@code NONE}): {@code joinHousehold}/{@code setupHousehold}
 * now also establish an authenticated session via {@code AccountSessionAuthenticator}, which needs
 * a real {@code WebApplicationContext} (for {@code AuthenticationConfiguration} autoconfiguration
 * and the {@code request}/{@code response} scopes) plus a thread-bound request (set up per test
 * below).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"librehousehold.security.oauth2-client.client-secret=test-client-secret"})
@Import(TestcontainersConfiguration.class)
@ExtendWith(InstancioExtension.class)
class MemberManagementServiceIT {

    @Autowired
    private MemberManagementService memberManagementService;

    @Autowired
    private HouseholdSetupService setupService;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InviteRepository inviteRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private HouseholdSetupResponse setupResponse;
    private Household existingHousehold;
    private final List<UUID> createdHouseholdIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
        var member = Instancio.create(Member.class);
        existingHousehold = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        setupResponse = setupService.setupHousehold(new HouseholdSetup(existingHousehold, member, localRegistration));
        createdHouseholdIds.add(existingHousehold.getId());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        for (var householdId : createdHouseholdIds) {
            inviteRepository.deleteByHouseholdId(householdId);
            memberRepository.deleteByHouseholdId(householdId);
            householdRepository.deleteById(householdId);
        }
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
            var registration = Instancio.create(MemberRegistration.class);
            memberManagementService.joinHousehold(token, registration);

            // when
            var result = memberManagementService.getMembers(existingHousehold.getId());

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class joinHousehold {

        @Test
        void validToken_persistsMemberInDatabase() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(MemberRegistration.class);

            // when
            memberManagementService.joinHousehold(token, registration);

            // then
            assertThat(memberRepository.findById(registration.getId()))
                    .hasValueSatisfying(m -> {
                        assertThat(m.householdId()).isEqualTo(existingHousehold.getId());
                        assertThat(m.isAdmin()).isFalse();
                    });
        }

        @Test
        void validRegistration_persistsHashedPasswordInAccountTable() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(MemberRegistration.class);

            // when
            memberManagementService.joinHousehold(token, registration);

            // then
            assertThat(accountRepository.findById(registration.getId()))
                    .hasValueSatisfying(account -> assertThat(passwordEncoder.matches(
                            registration.getLocalRegistration().getPassword(), account.passwordHash())).isTrue());
        }

        @Test
        void expiredToken_throwsInvalidInviteException() {
            // given — create an expired invite directly
            var expiredInvite = inviteRepository.save(new InviteEntity(
                    null, existingHousehold.getId(), UUID.randomUUID(), LocalDate.now().minusDays(1)
            ));
            var registration = Instancio.create(MemberRegistration.class);

            // when / then
            assertThatThrownBy(() -> memberManagementService.joinHousehold(expiredInvite.token(), registration))
                    .isInstanceOf(InvalidInviteException.class);
        }

        @Test
        void duplicateEmail_throwsMemberAlreadyExistsException() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(MemberRegistration.class);
            memberManagementService.joinHousehold(token, registration);

            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var token2 = setupService.setupHousehold(new HouseholdSetup(
                    household,
                    Instancio.create(Member.class),
                    Instancio.create(LocalRegistration.class)
            )).getInviteToken();
            var registration2 = Instancio.of(MemberRegistration.class)
                    .set(field(MemberRegistration::getEmail), registration.getEmail())
                    .create();

            // when / then
            assertThatThrownBy(() -> memberManagementService.joinHousehold(token2, registration2))
                    .isInstanceOf(MemberAlreadyExistsException.class);
        }
    }

    @Nested
    class isEmailAvailable {

        @Test
        void availableEmail_returnsTrue() {
            // given
            var email = "unknown-" + UUID.randomUUID() + "@example.com";

            // when
            var result = memberManagementService.isEmailAvailable(email);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void existingMemberEmail_returnsFalse() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(MemberRegistration.class);
            memberManagementService.joinHousehold(token, registration);

            // when
            var result = memberManagementService.isEmailAvailable(registration.getEmail());

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class findMemberIdByEmail {

        @Test
        void unknownEmail_returnsEmptyOptional() {
            // given
            var email = "unknown-" + UUID.randomUUID() + "@example.com";

            // when
            var result = memberManagementService.findMemberIdByEmail(email);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void knownEmail_returnsMemberId() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(MemberRegistration.class);
            memberManagementService.joinHousehold(token, registration);

            // when
            var result = memberManagementService.findMemberIdByEmail(registration.getEmail());

            // then
            assertThat(result).contains(registration.getId());
        }
    }

    @Nested
    class leaveHousehold {

        @Test
        void memberFound_removesFromDatabase() {
            // given
            var token = setupResponse.getInviteToken();
            var registration = Instancio.create(MemberRegistration.class);
            memberManagementService.joinHousehold(token, registration);

            // when
            memberManagementService.leaveHousehold(registration.getId());

            // then
            assertThat(memberRepository.existsById(registration.getId())).isFalse();
        }

        @Test
        void memberNotFound_throwsMemberNotFoundException() {
            // when / then
            assertThatThrownBy(() -> memberManagementService.leaveHousehold(UUID.randomUUID()))
                    .isInstanceOf(MemberNotFoundException.class);
        }

        @Test
        void memberIsAdmin_throwsHouseholdAdminCannotBeRemovedExceptionAndLeavesMemberInDatabase() {
            // given — the admin created in setUp() is the only member of existingHousehold
            var adminMemberId = memberManagementService.getMembers(existingHousehold.getId()).getFirst().getId();

            // when / then
            assertThatThrownBy(() -> memberManagementService.leaveHousehold(adminMemberId))
                    .isInstanceOf(HouseholdAdminCannotBeRemovedException.class);
            assertThat(memberRepository.existsById(adminMemberId)).isTrue();
        }
    }

    @Nested
    class getMemberWithHouseholdId {

        @Test
        void memberBelongsToDifferentHousehold_throwsMemberNotFoundException() {
            // given — member joined a second, unrelated household
            var otherHousehold = Instancio.create(Household.class);
            createdHouseholdIds.add(otherHousehold.getId());
            var otherToken = setupService.setupHousehold(new HouseholdSetup(
                    otherHousehold, Instancio.create(Member.class), Instancio.create(LocalRegistration.class))).getInviteToken();
            var memberOfOtherHousehold = memberManagementService.joinHousehold(otherToken, Instancio.create(MemberRegistration.class));

            // when / then
            assertThatThrownBy(() -> memberManagementService.getMember(existingHousehold.getId(), memberOfOtherHousehold.getId()))
                    .isInstanceOf(MemberNotFoundException.class);
        }
    }

    @Nested
    class removeMember {

        @Test
        void memberIsAdmin_throwsHouseholdAdminCannotBeRemovedExceptionAndLeavesMemberInDatabase() {
            // given — the admin created in setUp() is the only member of existingHousehold
            var adminMemberId = memberManagementService.getMembers(existingHousehold.getId()).getFirst().getId();

            // when / then
            assertThatThrownBy(() -> memberManagementService.removeMember(existingHousehold.getId(), adminMemberId))
                    .isInstanceOf(HouseholdAdminCannotBeRemovedException.class);
            assertThat(memberRepository.existsById(adminMemberId)).isTrue();
        }

        @Test
        void memberBelongsToDifferentHousehold_throwsMemberNotFoundExceptionAndLeavesMemberInDatabase() {
            // given — member joined a second, unrelated household
            var otherHousehold = Instancio.create(Household.class);
            createdHouseholdIds.add(otherHousehold.getId());
            var otherToken = setupService.setupHousehold(new HouseholdSetup(
                    otherHousehold, Instancio.create(Member.class), Instancio.create(LocalRegistration.class))).getInviteToken();
            var memberOfOtherHousehold = memberManagementService.joinHousehold(otherToken, Instancio.create(MemberRegistration.class));

            // when / then
            assertThatThrownBy(() -> memberManagementService.removeMember(existingHousehold.getId(), memberOfOtherHousehold.getId()))
                    .isInstanceOf(MemberNotFoundException.class);
            assertThat(memberRepository.existsById(memberOfOtherHousehold.getId())).isTrue();
        }
    }
}
