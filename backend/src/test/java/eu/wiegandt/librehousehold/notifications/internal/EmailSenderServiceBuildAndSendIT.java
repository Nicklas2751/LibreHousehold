package eu.wiegandt.librehousehold.notifications.internal;

import ch.martinelli.oss.testcontainers.mailpit.MailpitClient;
import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static ch.martinelli.oss.testcontainers.mailpit.assertions.MailpitAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves that {@link EmailSenderService#buildAndSend} works end-to-end against a real SMTP server,
 * independent of any single public {@code send*} wrapper: calls it directly with the real
 * {@code MessageSource}/{@code SpringTemplateEngine} beans (no mocks) and inspects the delivered
 * text and HTML parts via Mailpit. {@link EmailDeliveryIT} covers the public API surface instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "librehousehold.security.oauth2-client.client-secret=test-client-secret")
@Import(TestcontainersConfiguration.class)
class EmailSenderServiceBuildAndSendIT {

    @Autowired
    private EmailSenderService emailSenderService;

    @Autowired
    private MailpitClient mailpitClient;

    @Test
    void templateWithVariable_deliversTextAndHtmlPartsContainingVariable() {
        // given
        // recipient address is unique per test since both tests share the same Mailpit container
        var toEmail = "recipient-" + UUID.randomUUID() + "@example.com";
        var verificationLink = "https://example.com/link";

        // when
        emailSenderService.buildAndSend(toEmail, UUID.randomUUID(), "verification", "verification.subject",
                new Object[] {verificationLink}, Map.of("verificationLink", verificationLink));

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var message = assertThat(mailpitClient.getAllMessages())
                    .filteredOnRecipient(toEmail)
                    .singleElement()
                    .hasSubject("Verify your email address")
                    .getMessage();
            assertThat(mailpitClient.getMessagePlain(message.id())).contains(verificationLink);
            assertThat(mailpitClient.getMessageHtml(message.id())).contains(verificationLink);
        });
    }

    @Test
    void templateWithoutVariables_deliversMessage() {
        // given
        // recipient address is unique per test since both tests share the same Mailpit container
        var toEmail = "recipient-" + UUID.randomUUID() + "@example.com";

        // when
        emailSenderService.buildAndSend(toEmail, UUID.randomUUID(), "verification-deletion-warning",
                "verification-deletion-warning.subject", new Object[0], Map.of());

        // then
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(mailpitClient.getAllMessages())
                        .filteredOnRecipient(toEmail)
                        .singleElement()
                        .hasSubject("Your account will be deleted soon"));
    }
}
