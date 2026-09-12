package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.PasswordResetRequested;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountTokenRepository;
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

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * Calls the listener method directly instead of publishing {@link PasswordResetRequested} through
 * the real {@code ApplicationEventPublisher}: {@code @ApplicationModuleListener} still runs
 * {@code @Async} even when invoked directly on the autowired (proxied) bean, so the assertions
 * below poll with Awaitility rather than asserting immediately after the call returns. See
 * {@code AccountRegistrationListenerIT} for the same pattern.
 */
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
class PasswordResetRequestedListenerIT {

    @Autowired
    private PasswordResetRequestedListener listener;

    @Autowired
    private AccountTokenRepository accountTokenRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

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

    @Test
    void passwordResetRequested_issuesPasswordResetTokenAndSendsEmail() {
        // given
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();

        // when
        listener.on(new PasswordResetRequested(member.getId(), member.email()));

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(accountTokenRepository.findAll())
                    .anyMatch(token -> token.memberId().equals(member.getId()) && token.purpose().equals("PASSWORD_RESET"));
            verify(mailSender).send(any(MimeMessage.class));
        });
    }
}
