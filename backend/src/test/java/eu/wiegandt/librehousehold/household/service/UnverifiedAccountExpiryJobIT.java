package eu.wiegandt.librehousehold.household.service;

import ch.martinelli.oss.testcontainers.mailpit.MailpitClient;
import ch.martinelli.oss.testcontainers.mailpit.assertions.MailpitAssertions;
import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.instancio.Select.field;

/**
 * Runs against the real Mailpit container from {@link TestcontainersConfiguration}, which is shared
 * across IT classes through Spring's context cache: assertions therefore filter on the (random)
 * recipient address of the member created in each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "librehousehold.security.oauth2-client.client-secret=test-client-secret")
@Import(TestcontainersConfiguration.class)
@ExtendWith(InstancioExtension.class)
class UnverifiedAccountExpiryJobIT {

    private static final Duration MAIL_DELIVERY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LISTENER_COMPLETION_TIMEOUT = Duration.ofSeconds(10);

    // Only publications that a listener may still pick up or is still processing: FAILED (or
    // COMPLETED) entries from earlier tests must not block the cleanup of this one.
    private static final String COUNT_IN_PROGRESS_PUBLICATIONS_SQL = """
            SELECT COUNT(*) FROM event_publication
            WHERE status IN ('PUBLISHED', 'PROCESSING', 'RESUBMITTED') AND publication_date >= ?
            """;

    private final Instant testStartedAt = Instant.now();

    @Autowired
    private UnverifiedAccountExpiryJob job;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private MailpitClient mailpitClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID createdHouseholdId;

    @AfterEach
    void tearDown() {
        // The listeners run asynchronously after commit; deleting before they are done would pull
        // the member out from under them.
        await().atMost(LISTENER_COMPLETION_TIMEOUT).until(this::noPublicationInProgress);
        if (createdHouseholdId != null) {
            memberRepository.deleteByHouseholdId(createdHouseholdId);
            householdRepository.deleteById(createdHouseholdId);
        }
    }

    private boolean noPublicationInProgress() {
        return jdbcTemplate.queryForObject(COUNT_IN_PROGRESS_PUBLICATIONS_SQL, Long.class,
                Timestamp.from(testStartedAt)) == 0;
    }

    private MemberEntity createMember(boolean isAdmin) {
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        return memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .set(field(MemberEntity::isAdmin), isAdmin)
                .generate(field(MemberEntity::email), gen -> gen.net().email())
                .create());
    }

    @Test
    void run_accountPastWarningThresholdNotYetWarned_setsWarningTimestamp() {
        // given — grace-period-days=7, deletion-warning-hours-before=24 (see application.yaml):
        // registered 6 days and 1 hour ago is past the warning threshold but not the grace period.
        var member = createMember(false);
        var registeredAt = Instant.now().minus(Duration.ofDays(6)).minus(Duration.ofHours(1));
        accountRepository.save(new AccountEntity(member.getId(), "hash", false, registeredAt, null, 0, null));

        // when
        job.run();

        // then
        assertThat(accountRepository.findById(member.getId()))
                .hasValueSatisfying(account -> assertThat(account.verificationDeletionWarningSentAt()).isNotNull());
    }

    @Test
    void run_accountPastWarningThresholdNotYetWarned_deliversWarningEmail() {
        // given — registered 6 days and 1 hour ago: past the warning threshold, not the grace period
        var member = createMember(false);
        var registeredAt = Instant.now().minus(Duration.ofDays(6)).minus(Duration.ofHours(1));
        accountRepository.save(new AccountEntity(member.getId(), "hash", false, registeredAt, null, 0, null));

        // when
        job.run();

        // then
        await().atMost(MAIL_DELIVERY_TIMEOUT)
                .untilAsserted(() -> MailpitAssertions.assertThat(mailpitClient.getAllMessages())
                        .filteredOnRecipient(member.email())
                        .singleElement()
                        .hasSubject("Your account will be deleted soon"));
    }

    @Test
    void run_adminAccountPastGracePeriod_deletesEntireHousehold() {
        // given
        var member = createMember(true);
        var registeredAt = Instant.now().minus(Duration.ofDays(8));
        accountRepository.save(new AccountEntity(member.getId(), "hash", false, registeredAt, Instant.now(), 0, null));

        // when
        job.run();

        // then
        assertThat(householdRepository.existsById(member.householdId())).isFalse();
    }

    @Test
    void run_adminAccountPastGracePeriod_deliversHouseholdDeletedEmail() {
        // given
        var member = createMember(true);
        var registeredAt = Instant.now().minus(Duration.ofDays(8));
        accountRepository.save(new AccountEntity(member.getId(), "hash", false, registeredAt, Instant.now(), 0, null));

        // when
        job.run();

        // then
        await().atMost(MAIL_DELIVERY_TIMEOUT)
                .untilAsserted(() -> MailpitAssertions.assertThat(mailpitClient.getAllMessages())
                        .filteredOnRecipient(member.email())
                        .singleElement()
                        .hasSubject("Your household has been deleted"));
    }

    @Test
    void run_nonAdminAccountPastGracePeriod_removesOnlyThatMember() {
        // given
        var member = createMember(false);
        var registeredAt = Instant.now().minus(Duration.ofDays(8));
        accountRepository.save(new AccountEntity(member.getId(), "hash", false, registeredAt, Instant.now(), 0, null));

        // when
        job.run();

        // then
        assertThat(memberRepository.existsById(member.getId())).isFalse();
    }

    @Test
    void run_nonAdminAccountPastGracePeriod_deliversMemberRemovedEmail() {
        // given
        var member = createMember(false);
        var registeredAt = Instant.now().minus(Duration.ofDays(8));
        accountRepository.save(new AccountEntity(member.getId(), "hash", false, registeredAt, Instant.now(), 0, null));

        // when
        job.run();

        // then
        await().atMost(MAIL_DELIVERY_TIMEOUT)
                .untilAsserted(() -> MailpitAssertions.assertThat(mailpitClient.getAllMessages())
                        .filteredOnRecipient(member.email())
                        .singleElement()
                        .hasSubject("You have been removed from a household"));
    }
}
