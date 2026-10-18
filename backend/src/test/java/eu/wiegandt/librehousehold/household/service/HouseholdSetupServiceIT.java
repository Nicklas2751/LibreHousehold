package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.ExternalIdentity;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.household.PendingSocialRegistrationStore;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.exception.HouseholdAlreadyExistsException;
import eu.wiegandt.librehousehold.household.exception.MemberAlreadyExistsException;
import eu.wiegandt.librehousehold.household.mapper.HouseholdSetupMapper;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.model.Household;
import eu.wiegandt.librehousehold.model.HouseholdSetup;
import eu.wiegandt.librehousehold.model.LocalRegistration;
import eu.wiegandt.librehousehold.model.Member;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.instancio.Select.field;

/**
 * {@code webEnvironment = MOCK} (not {@code NONE}): {@code setupHousehold} now also establishes an
 * authenticated session via {@code AccountSessionAuthenticator}, which needs a real
 * {@code WebApplicationContext} (for {@code AuthenticationConfiguration} autoconfiguration and the
 * {@code request}/{@code response} scopes) plus a thread-bound request (bound per test via
 * {@link #bindRequest()}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"librehousehold.security.oauth2-client.client-secret=test-client-secret"})
@Import(TestcontainersConfiguration.class)
@ExtendWith(InstancioExtension.class)
class HouseholdSetupServiceIT {

    private static final Duration LISTENER_COMPLETION_TIMEOUT = Duration.ofSeconds(10);

    // Only publications that a listener may still pick up or is still processing: FAILED (or
    // COMPLETED) entries from earlier tests must not block the cleanup of this one.
    private static final String COUNT_IN_PROGRESS_PUBLICATIONS_SQL = """
            SELECT COUNT(*) FROM event_publication
            WHERE status IN ('PUBLISHED', 'PROCESSING', 'RESUBMITTED') AND publication_date >= ?
            """;

    private final Instant testStartedAt = Instant.now();

    @Autowired
    private HouseholdSetupService service;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InviteRepository inviteRepository;

    @Autowired
    private HouseholdSetupMapper householdSetupMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PendingSocialRegistrationStore pendingSocialRegistrationStore;

    @Autowired
    private MemberQuery memberQuery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID createdHouseholdId;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        // The listeners run asynchronously after commit and may still write rows referencing the
        // members (e.g. account_token); deleting before they are done violates those foreign keys.
        await().atMost(LISTENER_COMPLETION_TIMEOUT).until(this::noPublicationInProgress);
        if (createdHouseholdId != null) {
            inviteRepository.deleteByHouseholdId(createdHouseholdId);
            memberRepository.deleteByHouseholdId(createdHouseholdId);
            householdRepository.deleteById(createdHouseholdId);
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

    @Test
    void setupHousehold_validSetup_persistsHashedPasswordInAccountTable() {
        // given
        bindRequest();
        var member = memberWithValidEmail();
        var household = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        createdHouseholdId = household.getId();

        // when
        service.setupHousehold(new HouseholdSetup(household, member).localRegistration(localRegistration));

        // then
        assertThat(accountRepository.findById(member.getId()))
                .hasValueSatisfying(account ->
                        assertThat(passwordEncoder.matches(localRegistration.getPassword(), account.passwordHash())).isTrue());
    }

    @Test
    void setupHousehold_validSetup_persistsHouseholdInDatabase() {
        // given
        bindRequest();
        var member = memberWithValidEmail();
        var household = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        createdHouseholdId = household.getId();

        // when
        service.setupHousehold(new HouseholdSetup(household, member).localRegistration(localRegistration));

        // then
        assertThat(householdRepository.findById(household.getId()))
                .contains(householdSetupMapper.toHouseholdEntity(household));
    }

    @Test
    void setupHousehold_validSetup_persistsMemberInDatabase() {
        // given
        bindRequest();
        var member = memberWithValidEmail();
        var household = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        createdHouseholdId = household.getId();

        // when
        service.setupHousehold(new HouseholdSetup(household, member).localRegistration(localRegistration));

        // then
        assertThat(memberRepository.findById(member.getId()))
                .hasValueSatisfying(m -> {
                    assertThat(m.householdId()).isEqualTo(household.getId());
                    assertThat(m.isAdmin()).isTrue();
                });
    }

    @Test
    void setupHousehold_duplicateHouseholdId_throwsHouseholdAlreadyExistsException() {
        // given
        bindRequest();
        var member1 = memberWithValidEmail();
        var household = Instancio.create(Household.class);
        createdHouseholdId = household.getId();
        service.setupHousehold(new HouseholdSetup(household, member1).localRegistration(Instancio.create(LocalRegistration.class)));

        var member2 = memberWithValidEmail();
        var conflictingHousehold = Instancio.of(Household.class)
                .set(field(Household::getId), household.getId())
                .create();
        var localRegistration = Instancio.create(LocalRegistration.class);

        // when / then
        assertThatThrownBy(() -> service.setupHousehold(new HouseholdSetup(conflictingHousehold, member2).localRegistration(localRegistration)))
                .isInstanceOf(HouseholdAlreadyExistsException.class);
    }

    @Test
    void setupHousehold_duplicateMemberEmail_throwsMemberAlreadyExistsException() {
        // given
        bindRequest();
        var member1 = memberWithValidEmail();
        var household1 = Instancio.create(Household.class);
        createdHouseholdId = household1.getId();
        service.setupHousehold(new HouseholdSetup(household1, member1).localRegistration(Instancio.create(LocalRegistration.class)));

        var member2 = Instancio.of(Member.class)
                .set(field(Member::getEmail), member1.getEmail())
                .create();
        var household2 = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);

        // when / then
        assertThatThrownBy(() -> service.setupHousehold(new HouseholdSetup(household2, member2).localRegistration(localRegistration)))
                .isInstanceOf(MemberAlreadyExistsException.class);
    }

    @Test
    void setupHousehold_validSetup_inviteTokenStoredInDatabase() {
        // given
        bindRequest();
        var member = memberWithValidEmail();
        var household = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        createdHouseholdId = household.getId();

        // when
        var result = service.setupHousehold(new HouseholdSetup(household, member).localRegistration(localRegistration));

        // then
        assertThat(inviteRepository.findAll())
                .anyMatch(invite -> invite.token().equals(result.getInviteToken()));
    }

    @Test
    void setupHousehold_validSetup_inviteValidForSevenDays() {
        // given
        bindRequest();
        var member = memberWithValidEmail();
        var household = Instancio.create(Household.class);
        var localRegistration = Instancio.create(LocalRegistration.class);
        var expectedValidUntil = LocalDate.now().plusDays(7);
        createdHouseholdId = household.getId();

        // when
        var result = service.setupHousehold(new HouseholdSetup(household, member).localRegistration(localRegistration));

        // then
        assertThat(inviteRepository.findAll())
                .anyMatch(invite -> invite.token().equals(result.getInviteToken())
                        && invite.validUntil().equals(expectedValidUntil));
    }

    @Test
    void setupHousehold_pendingSocialRegistration_persistsMemberWithIdentityEmailLinkedToIdentity() {
        // given
        var household = Instancio.create(Household.class);
        var identity = new ExternalIdentity("google", "subject-" + UUID.randomUUID(),
                "social-" + UUID.randomUUID() + "@example.com", "Provider Name");
        createdHouseholdId = household.getId();
        bindRequest();
        pendingSocialRegistrationStore.store(identity);

        // when
        service.setupHousehold(new HouseholdSetup(household, memberWithValidEmail()));

        // then
        assertThat(memberQuery.findEmailByExternalIdentity(identity.registrationId(), identity.subject()))
                .contains(identity.email());
    }

    @Test
    void setupHousehold_pendingSocialRegistration_persistsVerifiedAccountWithoutPassword() {
        // given
        var household = Instancio.create(Household.class);
        var member = memberWithValidEmail();
        createdHouseholdId = household.getId();
        bindRequest();
        pendingSocialRegistrationStore.store(new ExternalIdentity("google", "subject-" + UUID.randomUUID(),
                "social-" + UUID.randomUUID() + "@example.com", null));

        // when
        service.setupHousehold(new HouseholdSetup(household, member));

        // then
        assertThat(accountRepository.findById(member.getId()))
                .get()
                .extracting(AccountEntity::passwordHash, AccountEntity::emailVerified)
                .containsExactly(null, true);
    }
}
