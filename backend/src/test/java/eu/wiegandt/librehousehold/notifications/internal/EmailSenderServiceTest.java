package eu.wiegandt.librehousehold.notifications.internal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSenderServiceTest {

    private static final String FRONTEND_BASE_URL = "https://household.example.com";

    @Mock
    private JavaMailSender mailSender;

    @Nested
    class sendVerificationEmail {

        @Test
        void toEmailAndToken_buildsMessageWithTokenLink() {
            // given
            var emailSenderService = new EmailSenderService(mailSender, FRONTEND_BASE_URL);
            var toEmail = "max@example.com";
            var token = UUID.randomUUID();

            // when
            emailSenderService.sendVerificationEmail(toEmail, token);

            // then
            var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            var message = captor.getValue();
            assertThat(message.getTo()).containsExactly(toEmail);
            // Path segment, not a query parameter: matches the SvelteKit route at
            // frontend/src/routes/verify-email/[token]/+page.svelte.
            assertThat(message.getText()).contains(FRONTEND_BASE_URL + "/verify-email/" + token);
        }
    }

    @Nested
    class sendVerificationDeletionWarningEmail {

        @Test
        void toEmail_sendsWarningMessage() {
            // given
            var emailSenderService = new EmailSenderService(mailSender, FRONTEND_BASE_URL);
            var toEmail = "max@example.com";

            // when
            emailSenderService.sendVerificationDeletionWarningEmail(toEmail);

            // then
            var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getTo()).containsExactly(toEmail);
        }
    }
}
