package eu.wiegandt.librehousehold.household.service;

import ch.martinelli.oss.testcontainers.mailpit.MailpitClient;
import ch.martinelli.oss.testcontainers.mailpit.assertions.MailpitAssertions;
import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.ExternalIdentity;
import eu.wiegandt.librehousehold.household.PasswordResetRequested;
import eu.wiegandt.librehousehold.household.PendingSocialRegistrationStore;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.exception.HouseholdAdminCannotBeRemovedException;
import eu.wiegandt.librehousehold.household.exception.InvalidInviteException;
import eu.wiegandt.librehousehold.household.exception.MemberAlreadyExistsException;
import eu.wiegandt.librehousehold.household.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.InviteEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.model.*;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.instancio.Select.field;

/**
 * {@code webEnvironment = MOCK} (not {@code NONE}): {@code joinHousehold}/{@code setupHousehold}
 * now also establish an authenticated session via {@code AccountSessionAuthenticator}, which needs
 * a real {@code WebApplicationContext} (for {@code AuthenticationConfiguration} autoconfiguration
 * and the {@code request}/{@code response} scopes) plus a thread-bound request (bound per test via
 * {@link #bindRequest()}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"librehousehold.security.oauth2-client.client-secret=test-client-secret"})
@Import(TestcontainersConfiguration.class)
@ExtendWith(InstancioExtension.class)
@RecordApplicationEvents
class MemberManagementServiceIT {

    private static final Duration LISTENER_COMPLETION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MAIL_DELIVERY_TIMEOUT = Duration.ofSeconds(10);

    // Only publications that a listener may still pick up or is still processing: FAILED (or
    // COMPLETED) entries from earlier tests must not block the cleanup of this one.
    private static final String COUNT_IN_PROGRESS_PUBLICATIONS_SQL = """
            SELECT COUNT(*) FROM event_publication
            WHERE status IN ('PUBLISHED', 'PROCESSING', 'RESUBMITTED') AND publication_date >= ?
            """;

    private final Instant testStartedAt = Instant.now();

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

    @Autowired
    private AccountService accountService;

    @Autowired
    private PendingSocialRegistrationStore pendingSocialRegistrationStore;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MailpitClient mailpitClient;

    private final List<UUID> createdHouseholdIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        // The listeners run asynchronously after commit and may still write rows referencing the
        // members (e.g. account_token); deleting before they are done violates those foreign keys.
        await().atMost(LISTENER_COMPLETION_TIMEOUT).until(this::noPublicationInProgress);
        for (var householdId : createdHouseholdIds) {
            inviteRepository.deleteByHouseholdId(householdId);
            memberRepository.deleteByHouseholdId(householdId);
            householdRepository.deleteById(householdId);
        }
    }

    private boolean noPublicationInProgress() {
        return jdbcTemplate.queryForObject(COUNT_IN_PROGRESS_PUBLICATIONS_SQL, Long.class,
                Timestamp.from(testStartedAt)) == 0;
    }

    private void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
    }

    private Member memberWithValidEmail() {
        return Instancio.of(Member.class)
                .generate(field(Member::getEmail), gen -> gen.net().email())
                .create();
    }

    private MemberRegistration registrationWithValidEmail() {
        return Instancio.of(MemberRegistration.class)
                .generate(field(MemberRegistration::getEmail), gen -> gen.net().email())
                .create();
    }

    private MemberEntity memberEntityWithValidEmail(UUID householdId) {
        return Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), householdId)
                .generate(field(MemberEntity::email), gen -> gen.net().email())
                .create();
    }

    private HouseholdSetup localSetup(Household household, Member admin) {
        return new HouseholdSetup(household, admin).localRegistration(Instancio.create(LocalRegistration.class));
    }

    @Nested
    class getMembers {

        @Test
        void adminIsOnlyMember_returnsOneAdminMember() {
            // given
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            setupService.setupHousehold(localSetup(household, memberWithValidEmail()));

            // when
            var result = memberManagementService.getMembers(household.getId());

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getIsAdmin()).contains(true);
        }

        @Test
        void additionalMemberJoined_returnsBothMembers() {
            // given
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var token = setupService.setupHousehold(localSetup(household, memberWithValidEmail())).getInviteToken();
            memberManagementService.joinHousehold(token, registrationWithValidEmail());

            // when
            var result = memberManagementService.getMembers(household.getId());

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class joinHousehold {

        @Test
        void validToken_persistsMemberInDatabase() {
            // given
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var token = setupService.setupHousehold(localSetup(household, memberWithValidEmail())).getInviteToken();
            var registration = registrationWithValidEmail();

            // when
            memberManagementService.joinHousehold(token, registration);

            // then
            assertThat(memberRepository.findById(registration.getId()))
                    .hasValueSatisfying(m -> {
                        assertThat(m.householdId()).isEqualTo(household.getId());
                        assertThat(m.isAdmin()).isFalse();
                    });
        }

        @Test
        void validRegistration_persistsHashedPasswordInAccountTable() {
            // given
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var token = setupService.setupHousehold(localSetup(household, memberWithValidEmail())).getInviteToken();
            var registration = registrationWithValidEmail();

            // when
            memberManagementService.joinHousehold(token, registration);

            // then
            assertThat(accountRepository.findById(registration.getId()))
                    .hasValueSatisfying(account -> assertThat(passwordEncoder.matches(
                            registration.getLocalRegistration().orElseThrow().getPassword(), account.passwordHash())).isTrue());
        }

        @Test
        void expiredToken_throwsInvalidInviteException() {
            // given — create an expired invite directly
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            createdHouseholdIds.add(household.id());
            var expiredInvite = inviteRepository.save(new InviteEntity(
                    null, household.id(), UUID.randomUUID(), LocalDate.now().minusDays(1)
            ));
            var registration = registrationWithValidEmail();

            // when / then
            assertThatThrownBy(() -> memberManagementService.joinHousehold(expiredInvite.token(), registration))
                    .isInstanceOf(InvalidInviteException.class);
        }

        @Test
        void duplicateEmail_throwsMemberAlreadyExistsException() {
            // given
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var token = setupService.setupHousehold(localSetup(household, memberWithValidEmail())).getInviteToken();
            var registration = registrationWithValidEmail();
            memberManagementService.joinHousehold(token, registration);

            var otherHousehold = Instancio.create(Household.class);
            createdHouseholdIds.add(otherHousehold.getId());
            var token2 = setupService.setupHousehold(localSetup(otherHousehold, memberWithValidEmail())).getInviteToken();
            var registration2 = Instancio.of(MemberRegistration.class)
                    .set(field(MemberRegistration::getEmail), registration.getEmail())
                    .create();

            // when / then
            assertThatThrownBy(() -> memberManagementService.joinHousehold(token2, registration2))
                    .isInstanceOf(MemberAlreadyExistsException.class);
        }
    }

    @Nested
    class joinHouseholdViaSocialLogin {

        @Test
        void pendingRegistration_persistsMemberWithIdentityEmailLinkedToIdentity() {
            // given
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var token = setupService.setupHousehold(localSetup(household, memberWithValidEmail())).getInviteToken();
            var identity = new ExternalIdentity("google", "subject-" + UUID.randomUUID(),
                    "social-" + UUID.randomUUID() + "@example.com", "Provider Name");
            bindRequest();
            pendingSocialRegistrationStore.store(identity);

            // when
            memberManagementService.joinHousehold(token, Instancio.of(MemberRegistration.class)
                    .ignore(field(MemberRegistration::getLocalRegistration))
                    .create());

            // then
            assertThat(memberManagementService.findEmailByExternalIdentity(identity.registrationId(), identity.subject()))
                    .contains(identity.email());
        }

        @Test
        void pendingRegistration_persistsVerifiedAccountWithoutPassword() {
            // given
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var token = setupService.setupHousehold(localSetup(household, memberWithValidEmail())).getInviteToken();
            var registration = Instancio.of(MemberRegistration.class)
                    .ignore(field(MemberRegistration::getLocalRegistration))
                    .create();
            bindRequest();
            pendingSocialRegistrationStore.store(new ExternalIdentity("google", "subject-" + UUID.randomUUID(),
                    "social-" + UUID.randomUUID() + "@example.com", null));

            // when
            memberManagementService.joinHousehold(token, registration);

            // then
            assertThat(accountRepository.findById(registration.getId()))
                    .get()
                    .extracting(AccountEntity::passwordHash, AccountEntity::emailVerified)
                    .containsExactly(null, true);
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
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var token = setupService.setupHousehold(localSetup(household, memberWithValidEmail())).getInviteToken();
            var registration = registrationWithValidEmail();
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
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var token = setupService.setupHousehold(localSetup(household, memberWithValidEmail())).getInviteToken();
            var registration = registrationWithValidEmail();
            memberManagementService.joinHousehold(token, registration);

            // when
            var result = memberManagementService.findMemberIdByEmail(registration.getEmail());

            // then
            assertThat(result).contains(registration.getId());
        }
    }

    @Nested
    class findEmailByExternalIdentity {

        @Test
        void linkedIdentity_returnsEmailOfLinkedMember() {
            // given
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var token = setupService.setupHousehold(localSetup(household, memberWithValidEmail())).getInviteToken();
            var registration = registrationWithValidEmail();
            var member = memberManagementService.joinHousehold(token, registration);
            var externalSubject = UUID.randomUUID().toString();
            accountService.linkExternalIdentity(member.getId(), "dev-provider", externalSubject);

            // when
            var result = memberManagementService.findEmailByExternalIdentity("dev-provider", externalSubject);

            // then
            assertThat(result).contains(registration.getEmail());
        }
    }

    @Nested
    class resendVerificationEmail {

        @Test
        void unverifiedMember_deliversVerificationEmail() {
            // given
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            createdHouseholdIds.add(household.id());
            var member = memberRepository.save(memberEntityWithValidEmail(household.id()));
            accountService.createAccount(member.getId(), "correct horse battery staple");

            // when
            memberManagementService.resendVerificationEmail(member.getId());

            // then
            await().atMost(MAIL_DELIVERY_TIMEOUT)
                    .untilAsserted(() -> MailpitAssertions.assertThat(mailpitClient.getAllMessages())
                            .filteredOnRecipient(member.email())
                            .singleElement()
                            .hasSubject("Verify your email address"));
        }
    }

    @Nested
    class requestPasswordReset {

        @Test
        void accountWithPassword_publishesPasswordResetRequested() {
            // given
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            createdHouseholdIds.add(household.id());
            var member = memberRepository.save(memberEntityWithValidEmail(household.id()));
            accountService.createAccount(member.getId(), "correct horse battery staple");

            // when
            memberManagementService.requestPasswordReset(member.email());

            // then
            assertThat(applicationEvents.stream(PasswordResetRequested.class))
                    .containsExactly(new PasswordResetRequested(member.getId(), member.email()));
        }

        @Test
        void federatedAccountWithoutPassword_publishesNothing() {
            // given
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            createdHouseholdIds.add(household.id());
            var member = memberRepository.save(memberEntityWithValidEmail(household.id()));
            accountService.createFederatedAccount(member.getId());

            // when
            memberManagementService.requestPasswordReset(member.email());

            // then
            assertThat(applicationEvents.stream(PasswordResetRequested.class)).isEmpty();
        }
    }

    @Nested
    class leaveHousehold {

        @Test
        void memberFound_removesFromDatabase() {
            // given
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var token = setupService.setupHousehold(localSetup(household, memberWithValidEmail())).getInviteToken();
            var registration = registrationWithValidEmail();
            memberManagementService.joinHousehold(token, registration);
            // the verification token listener must not race the removal below
            await().atMost(LISTENER_COMPLETION_TIMEOUT).until(MemberManagementServiceIT.this::noPublicationInProgress);

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
            // given
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var admin = memberWithValidEmail();
            setupService.setupHousehold(localSetup(household, admin));

            // when / then
            assertThatThrownBy(() -> memberManagementService.leaveHousehold(admin.getId()))
                    .isInstanceOf(HouseholdAdminCannotBeRemovedException.class);
            assertThat(memberRepository.existsById(admin.getId())).isTrue();
        }
    }

    @Nested
    class getMemberWithHouseholdId {

        @Test
        void memberBelongsToDifferentHousehold_throwsMemberNotFoundException() {
            // given — member joined a second, unrelated household
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            setupService.setupHousehold(localSetup(household, memberWithValidEmail()));
            var otherHousehold = Instancio.create(Household.class);
            createdHouseholdIds.add(otherHousehold.getId());
            var otherToken = setupService.setupHousehold(localSetup(otherHousehold, memberWithValidEmail())).getInviteToken();
            var memberOfOtherHousehold = memberManagementService.joinHousehold(otherToken, registrationWithValidEmail());

            // when / then
            assertThatThrownBy(() -> memberManagementService.getMember(household.getId(), memberOfOtherHousehold.getId()))
                    .isInstanceOf(MemberNotFoundException.class);
        }
    }

    @Nested
    class removeMember {

        @Test
        void memberIsAdmin_throwsHouseholdAdminCannotBeRemovedExceptionAndLeavesMemberInDatabase() {
            // given
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            var admin = memberWithValidEmail();
            setupService.setupHousehold(localSetup(household, admin));

            // when / then
            assertThatThrownBy(() -> memberManagementService.removeMember(household.getId(), admin.getId()))
                    .isInstanceOf(HouseholdAdminCannotBeRemovedException.class);
            assertThat(memberRepository.existsById(admin.getId())).isTrue();
        }

        @Test
        void memberBelongsToDifferentHousehold_throwsMemberNotFoundExceptionAndLeavesMemberInDatabase() {
            // given — member joined a second, unrelated household
            bindRequest();
            var household = Instancio.create(Household.class);
            createdHouseholdIds.add(household.getId());
            setupService.setupHousehold(localSetup(household, memberWithValidEmail()));
            var otherHousehold = Instancio.create(Household.class);
            createdHouseholdIds.add(otherHousehold.getId());
            var otherToken = setupService.setupHousehold(localSetup(otherHousehold, memberWithValidEmail())).getInviteToken();
            var memberOfOtherHousehold = memberManagementService.joinHousehold(otherToken, registrationWithValidEmail());

            // when / then
            assertThatThrownBy(() -> memberManagementService.removeMember(household.getId(), memberOfOtherHousehold.getId()))
                    .isInstanceOf(MemberNotFoundException.class);
            assertThat(memberRepository.existsById(memberOfOtherHousehold.getId())).isTrue();
        }
    }
}
