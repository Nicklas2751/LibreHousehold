package eu.wiegandt.librehousehold.notifications.internal;

import ch.martinelli.oss.testcontainers.mailpit.MailpitClient;
import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.UUID;

import static ch.martinelli.oss.testcontainers.mailpit.assertions.MailpitAssertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The only test in the {@code notifications} module running against a real SMTP server. Its sole
 * purpose is verifying that the {@code spring.mail.*} properties are actually picked up by Boot's
 * mail autoconfiguration and that {@link EmailSenderService} produces a message a real SMTP server
 * accepts and delivers end-to-end. All other mail-related edge cases (recipients, templating,
 * exception scenarios) are deliberately not repeated here — see the mocked {@code JavaMailSenderImpl}
 * tests in {@link EmailSenderServiceTest} and {@link AccountRegistrationListenerIT} for those.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "librehousehold.security.oauth2-client.client-secret=test-client-secret")
@Import(TestcontainersConfiguration.class)
class EmailDeliveryIT {

    @Autowired
    private EmailSenderService emailSenderService;

    @Autowired
    private MailpitClient mailpitClient;

    @Test
    void sendVerificationEmail_realSmtpServer_deliversEmailViaMailpit() {
        // given
        var toEmail = "recipient@example.com";

        // when
        emailSenderService.sendVerificationEmail(toEmail, UUID.randomUUID());

        // then
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(mailpitClient.getAllMessages())
                        .singleElement()
                        .hasSubject("Verify your email address")
                        .hasRecipient(toEmail));
    }
}
