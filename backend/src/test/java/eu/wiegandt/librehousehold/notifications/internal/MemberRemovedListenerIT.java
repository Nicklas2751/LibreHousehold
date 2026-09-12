package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.MemberRemoved;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * Publishes {@link MemberRemoved} through the real {@code ApplicationEventPublisher} (unlike
 * {@code AccountRegistrationListenerIT}/{@code PasswordResetRequestedListenerIT}, which call the
 * listener directly): the event already carries all member/household data, so no member row needs
 * to exist in the database beforehand, and publishing for real additionally proves that
 * {@code @ApplicationModuleListener} is correctly registered for this event type.
 *
 * <p>Publishing happens inside an explicit, committed transaction ({@link TransactionTemplate}):
 * {@code @ApplicationModuleListener} is a {@code @TransactionalEventListener} with the default
 * {@code AFTER_COMMIT} phase, which Spring silently skips (never invokes) if no transaction is
 * active at publish time - a plain {@code eventPublisher.publishEvent(...)} call in a test method
 * without one would never reach the listener.
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
class MemberRemovedListenerIT {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // Mocked as the concrete JavaMailSenderImpl (not the JavaMailSender interface): Boot's
    // MailHealthContributorAutoConfiguration looks up beans by the concrete type specifically, and
    // an interface-typed mock would not satisfy that lookup, breaking context startup.
    @MockitoBean
    private JavaMailSenderImpl mailSender;

    @Test
    void memberRemoved_validEvent_sendsOneEmail() {
        // given
        var event = new MemberRemoved(UUID.randomUUID(), "Anna", "anna@example.com", "Musterhaushalt");
        doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();

        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(mailSender).send(any(MimeMessage.class)));
    }
}
