package eu.wiegandt.librehousehold.household.service;

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
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

// Excludes MailpitAutoConfiguration: it would otherwise register its own real JavaMailSender bean
// alongside Boot's own (triggered by the mocked JavaMailSenderImpl below needing a bean of that
// exact declared type to override), leaving two JavaMailSender candidates and breaking the
// autowiring in EmailSenderService with a NoUniqueBeanDefinitionException.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "librehousehold.security.oauth2-client.client-secret=test-client-secret",
        "spring.mail.host=localhost",
        "spring.autoconfigure.exclude=ch.martinelli.oss.testcontainers.mailpit.MailpitAutoConfiguration"
})
@Import(TestcontainersConfiguration.class)
@ExtendWith(InstancioExtension.class)
class UnverifiedAccountExpiryJobIT {

    @Autowired
    private UnverifiedAccountExpiryJob job;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccountRepository accountRepository;

    // Mocked as the concrete JavaMailSenderImpl (not the JavaMailSender interface): Boot's
    // MailHealthContributorAutoConfiguration looks up beans by the concrete type specifically, and
    // an interface-typed mock would not satisfy that lookup, breaking context startup.
    @MockitoBean
    private JavaMailSenderImpl mailSender;

    private UUID createdHouseholdId;

    @AfterEach
    void tearDown() {
        if (createdHouseholdId != null) {
            memberRepository.deleteByHouseholdId(createdHouseholdId);
            householdRepository.deleteById(createdHouseholdId);
        }
    }

    private MemberEntity createMember(boolean isAdmin) {
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        return memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .set(field(MemberEntity::isAdmin), isAdmin)
                .create());
    }

    @Test
    void run_accountPastWarningThresholdNotYetWarned_sendsWarningEmailAndSetsTimestamp() {
        // given — grace-period-days=7, deletion-warning-hours-before=24 (see application.yaml):
        // registered 6 days and 1 hour ago is past the warning threshold but not the grace period.
        var member = createMember(false);
        var registeredAt = Instant.now().minus(Duration.ofDays(6)).minus(Duration.ofHours(1));
        accountRepository.save(new AccountEntity(member.getId(), "hash", false, registeredAt, null));

        // when
        job.run();

        // then
        assertThat(accountRepository.findById(member.getId()))
                .hasValueSatisfying(account -> assertThat(account.verificationDeletionWarningSentAt()).isNotNull());
    }

    @Test
    void run_adminAccountPastGracePeriod_deletesEntireHousehold() {
        // given
        var member = createMember(true);
        var registeredAt = Instant.now().minus(Duration.ofDays(8));
        accountRepository.save(new AccountEntity(member.getId(), "hash", false, registeredAt, Instant.now()));
        var householdId = createdHouseholdId;

        // when
        job.run();

        // then
        assertThat(householdRepository.existsById(householdId)).isFalse();
        createdHouseholdId = null;
    }

    @Test
    void run_nonAdminAccountPastGracePeriod_removesOnlyThatMember() {
        // given
        var member = createMember(false);
        var registeredAt = Instant.now().minus(Duration.ofDays(8));
        accountRepository.save(new AccountEntity(member.getId(), "hash", false, registeredAt, Instant.now()));

        // when
        job.run();

        // then
        assertThat(memberRepository.existsById(member.getId())).isFalse();
    }
}
