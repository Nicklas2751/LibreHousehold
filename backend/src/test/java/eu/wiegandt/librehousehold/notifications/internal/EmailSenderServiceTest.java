package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.model.UserPreferences;
import eu.wiegandt.librehousehold.usersettings.PreferencesQuery;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSenderServiceTest {

    private static final String FRONTEND_BASE_URL = "https://household.example.com";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MessageSource emailMessageSource;

    @Mock
    private SpringTemplateEngine emailTemplateEngine;

    @Mock
    private PreferencesQuery preferencesQuery;

    @Nested
    class sendVerificationEmail {

        @Test
        void germanPreference_sendsGermanSubjectAndBody() throws Exception {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var toEmail = "max@example.com";
            var memberId = UUID.randomUUID();
            var token = UUID.randomUUID();
            var expectedLink = FRONTEND_BASE_URL + "/verify-email/" + token;
            doReturn(new UserPreferences().language(UserPreferences.LanguageEnum.DE))
                    .when(preferencesQuery).getPreferencesOrDefault(memberId);
            doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
            doReturn("Bestätige deine E-Mail-Adresse").when(emailMessageSource)
                    .getMessage(eq("verification.subject"), any(), eq(Locale.GERMAN));
            doReturn("Bitte bestätige deine E-Mail-Adresse, indem du diesen Link öffnest: " + expectedLink)
                    .when(emailMessageSource)
                    .getMessage(eq("verification.body"), any(), eq(Locale.GERMAN));
            doReturn("<html>de</html>").when(emailTemplateEngine).process(eq("verification"), any(Context.class));

            // when
            emailSenderService.sendVerificationEmail(toEmail, memberId, token);

            // then
            var captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());
            var message = captor.getValue();
            assertThat(message.getSubject()).isEqualTo("Bestätige deine E-Mail-Adresse");
        }

        @Test
        void noPreferenceSet_fallsBackToEnglish() throws Exception {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var toEmail = "max@example.com";
            var memberId = UUID.randomUUID();
            var token = UUID.randomUUID();
            doReturn(new UserPreferences()).when(preferencesQuery).getPreferencesOrDefault(memberId);
            doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
            doReturn("Verify your email address").when(emailMessageSource)
                    .getMessage(eq("verification.subject"), any(), eq(Locale.ENGLISH));
            doReturn("Please verify your email address by opening this link: " + FRONTEND_BASE_URL + "/verify-email/" + token)
                    .when(emailMessageSource).getMessage(eq("verification.body"), any(), eq(Locale.ENGLISH));
            doReturn("<html>en</html>").when(emailTemplateEngine).process(eq("verification"), any(Context.class));

            // when
            emailSenderService.sendVerificationEmail(toEmail, memberId, token);

            // then
            var captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(bodyPartContent(captor.getValue(), 0))
                    // Path segment, not a query parameter: matches the SvelteKit route at
                    // frontend/src/routes/verify-email/[token]/+page.svelte.
                    .contains(FRONTEND_BASE_URL + "/verify-email/" + token);
        }
    }

    @Nested
    class sendPasswordResetEmail {

        @Test
        void noPreferenceSet_fallsBackToEnglish() throws Exception {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var toEmail = "max@example.com";
            var memberId = UUID.randomUUID();
            var token = UUID.randomUUID();
            var expectedLink = FRONTEND_BASE_URL + "/reset-password/" + token;
            doReturn(new UserPreferences()).when(preferencesQuery).getPreferencesOrDefault(memberId);
            doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
            doReturn("Reset your password").when(emailMessageSource)
                    .getMessage(eq("password-reset.subject"), any(), eq(Locale.ENGLISH));
            doReturn("Please reset your password by opening this link: " + expectedLink)
                    .when(emailMessageSource).getMessage(eq("password-reset.body"), any(), eq(Locale.ENGLISH));
            doReturn("<html>en</html>").when(emailTemplateEngine).process(eq("password-reset"), any(Context.class));

            // when
            emailSenderService.sendPasswordResetEmail(toEmail, memberId, token);

            // then
            var captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(bodyPartContent(captor.getValue(), 0)).contains(expectedLink);
        }
    }

    @Nested
    class sendVerificationDeletionWarningEmail {

        @Test
        void germanPreference_sendsGermanSubjectAndBody() throws Exception {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var toEmail = "max@example.com";
            var memberId = UUID.randomUUID();
            doReturn(new UserPreferences().language(UserPreferences.LanguageEnum.DE))
                    .when(preferencesQuery).getPreferencesOrDefault(memberId);
            doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
            doReturn("Dein Konto wird bald gelöscht").when(emailMessageSource)
                    .getMessage(eq("verification-deletion-warning.subject"), any(), eq(Locale.GERMAN));
            doReturn("Die E-Mail-Adresse deines Kontos ist noch nicht bestätigt.").when(emailMessageSource)
                    .getMessage(eq("verification-deletion-warning.body"), any(), eq(Locale.GERMAN));
            doReturn("<html>de</html>").when(emailTemplateEngine)
                    .process(eq("verification-deletion-warning"), any(Context.class));

            // when
            emailSenderService.sendVerificationDeletionWarningEmail(toEmail, memberId);

            // then
            var captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getSubject()).isEqualTo("Dein Konto wird bald gelöscht");
        }
    }

    @Nested
    class sendHouseholdDeletedEmail {

        @Test
        void distinctMemberAndHouseholdName_passesArgumentsInOrder() {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var toEmail = "max@example.com";
            var memberId = UUID.randomUUID();
            var memberName = "Max Mustermann";
            var householdName = "Musterhaushalt";
            doReturn(new UserPreferences()).when(preferencesQuery).getPreferencesOrDefault(memberId);
            doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
            doReturn("Your household has been deleted").when(emailMessageSource)
                    .getMessage(eq("household-deleted.subject"), any(), eq(Locale.ENGLISH));
            doReturn("Hi " + memberName + ", \"" + householdName + "\" has been deleted.").when(emailMessageSource)
                    .getMessage(eq("household-deleted.body"), any(), eq(Locale.ENGLISH));
            doReturn("<html>en</html>").when(emailTemplateEngine).process(eq("household-deleted"), any(Context.class));

            // when
            emailSenderService.sendHouseholdDeletedEmail(toEmail, memberId, memberName, householdName);

            // then
            var messageArgsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(emailMessageSource).getMessage(eq("household-deleted.body"), messageArgsCaptor.capture(), eq(Locale.ENGLISH));
            assertThat(messageArgsCaptor.getValue()).containsExactly(memberName, householdName);
        }
    }

    @Nested
    class sendMemberRemovedEmail {

        @Test
        void distinctMemberAndHouseholdName_passesArgumentsInOrder() {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var toEmail = "max@example.com";
            var memberId = UUID.randomUUID();
            var memberName = "Max Mustermann";
            var householdName = "Musterhaushalt";
            doReturn(new UserPreferences()).when(preferencesQuery).getPreferencesOrDefault(memberId);
            doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
            doReturn("You have been removed from a household").when(emailMessageSource)
                    .getMessage(eq("member-removed.subject"), any(), eq(Locale.ENGLISH));
            doReturn("Hi " + memberName + ", removed from \"" + householdName + "\".").when(emailMessageSource)
                    .getMessage(eq("member-removed.body"), any(), eq(Locale.ENGLISH));
            doReturn("<html>en</html>").when(emailTemplateEngine).process(eq("member-removed"), any(Context.class));

            // when
            emailSenderService.sendMemberRemovedEmail(toEmail, memberId, memberName, householdName);

            // then
            var messageArgsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(emailMessageSource).getMessage(eq("member-removed.body"), messageArgsCaptor.capture(), eq(Locale.ENGLISH));
            assertThat(messageArgsCaptor.getValue()).containsExactly(memberName, householdName);
        }
    }

    @Nested
    class resolveLocale {

        @Test
        void germanPreference_returnsGermanLocale() {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var memberId = UUID.randomUUID();
            doReturn(new UserPreferences().language(UserPreferences.LanguageEnum.DE))
                    .when(preferencesQuery).getPreferencesOrDefault(memberId);

            // when
            var locale = emailSenderService.resolveLocale(memberId);

            // then
            assertThat(locale).isEqualTo(Locale.forLanguageTag("de"));
        }

        @Test
        void englishPreference_returnsEnglishLocale() {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var memberId = UUID.randomUUID();
            doReturn(new UserPreferences().language(UserPreferences.LanguageEnum.EN))
                    .when(preferencesQuery).getPreferencesOrDefault(memberId);

            // when
            var locale = emailSenderService.resolveLocale(memberId);

            // then
            assertThat(locale).isEqualTo(Locale.forLanguageTag("en"));
        }

        @Test
        void noPreferenceSet_fallsBackToEnglishLocale() {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var memberId = UUID.randomUUID();
            doReturn(new UserPreferences()).when(preferencesQuery).getPreferencesOrDefault(memberId);

            // when
            var locale = emailSenderService.resolveLocale(memberId);

            // then
            assertThat(locale).isEqualTo(Locale.ENGLISH);
        }
    }

    @Nested
    class verificationLink {

        @Test
        void token_buildsFrontendVerificationUrl() {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var token = UUID.randomUUID();

            // when
            var link = emailSenderService.verificationLink(token);

            // then
            assertThat(link).isEqualTo(FRONTEND_BASE_URL + "/verify-email/" + token);
        }
    }

    @Nested
    class passwordResetLink {

        @Test
        void token_buildsFrontendResetPasswordUrl() {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var token = UUID.randomUUID();

            // when
            var link = emailSenderService.passwordResetLink(token);

            // then
            assertThat(link).isEqualTo(FRONTEND_BASE_URL + "/reset-password/" + token);
        }
    }

    @Nested
    class buildAndSend {

        @Test
        void templateArguments_sendsMessageBuiltFromMessageSourceAndTemplateEngine() throws Exception {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var toEmail = "max@example.com";
            var memberId = UUID.randomUUID();
            var templateName = "some-template";
            var subjectKey = "some-template.subject";
            var firstArg = "firstArg";
            var secondArg = "secondArg";
            doReturn(new UserPreferences()).when(preferencesQuery).getPreferencesOrDefault(memberId);
            doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
            doReturn("Resolved subject").when(emailMessageSource)
                    .getMessage(eq(subjectKey), any(), eq(Locale.ENGLISH));
            doReturn("Resolved body with " + firstArg + " and " + secondArg).when(emailMessageSource)
                    .getMessage(eq(templateName + ".body"), any(), eq(Locale.ENGLISH));
            doReturn("<html>Resolved html</html>").when(emailTemplateEngine)
                    .process(eq(templateName), any(Context.class));

            // when
            emailSenderService.buildAndSend(toEmail, memberId, templateName, subjectKey,
                    new Object[] {firstArg, secondArg}, Map.of("firstArg", firstArg, "secondArg", secondArg));

            // then
            var messageArgsCaptor = ArgumentCaptor.forClass(Object[].class);
            verify(emailMessageSource).getMessage(eq(templateName + ".body"), messageArgsCaptor.capture(), eq(Locale.ENGLISH));
            assertThat(messageArgsCaptor.getValue()).containsExactly(firstArg, secondArg);
        }

        @Test
        void resolvedSubject_setsSubjectOnMessage() throws Exception {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var toEmail = "max@example.com";
            var memberId = UUID.randomUUID();
            var templateName = "some-template";
            var subjectKey = "some-template.subject";
            doReturn(new UserPreferences()).when(preferencesQuery).getPreferencesOrDefault(memberId);
            doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
            doReturn("Resolved subject").when(emailMessageSource)
                    .getMessage(eq(subjectKey), any(), eq(Locale.ENGLISH));
            doReturn("Resolved body").when(emailMessageSource)
                    .getMessage(eq(templateName + ".body"), any(), eq(Locale.ENGLISH));
            doReturn("<html>Resolved html</html>").when(emailTemplateEngine)
                    .process(eq(templateName), any(Context.class));

            // when
            emailSenderService.buildAndSend(toEmail, memberId, templateName, subjectKey, new Object[0], Map.of());

            // then
            var captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getSubject()).isEqualTo("Resolved subject");
        }

        @Test
        void resolvedHtmlFromTemplateEngine_setsHtmlBodyPart() throws Exception {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var toEmail = "max@example.com";
            var memberId = UUID.randomUUID();
            var templateName = "some-template";
            var subjectKey = "some-template.subject";
            doReturn(new UserPreferences()).when(preferencesQuery).getPreferencesOrDefault(memberId);
            doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
            doReturn("Resolved subject").when(emailMessageSource)
                    .getMessage(eq(subjectKey), any(), eq(Locale.ENGLISH));
            doReturn("Resolved body").when(emailMessageSource)
                    .getMessage(eq(templateName + ".body"), any(), eq(Locale.ENGLISH));
            doReturn("<html>Resolved html</html>").when(emailTemplateEngine)
                    .process(eq(templateName), any(Context.class));

            // when
            emailSenderService.buildAndSend(toEmail, memberId, templateName, subjectKey, new Object[0], Map.of());

            // then
            var captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(bodyPartContent(captor.getValue(), 1)).isEqualTo("<html>Resolved html</html>");
        }

        @Test
        void recipient_setsToEmailAddressOnMessage() throws Exception {
            // given
            var emailSenderService = new EmailSenderService(mailSender, emailMessageSource, emailTemplateEngine,
                    preferencesQuery, FRONTEND_BASE_URL);
            var toEmail = "max@example.com";
            var memberId = UUID.randomUUID();
            var templateName = "some-template";
            var subjectKey = "some-template.subject";
            doReturn(new UserPreferences()).when(preferencesQuery).getPreferencesOrDefault(memberId);
            doReturn(new MimeMessage((Session) null)).when(mailSender).createMimeMessage();
            doReturn("Resolved subject").when(emailMessageSource)
                    .getMessage(eq(subjectKey), any(), eq(Locale.ENGLISH));
            doReturn("Resolved body").when(emailMessageSource)
                    .getMessage(eq(templateName + ".body"), any(), eq(Locale.ENGLISH));
            doReturn("<html>Resolved html</html>").when(emailTemplateEngine)
                    .process(eq(templateName), any(Context.class));

            // when
            emailSenderService.buildAndSend(toEmail, memberId, templateName, subjectKey, new Object[0], Map.of());

            // then
            var captor = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(captor.capture());
            assertThat(captor.getValue().getAllRecipients()).extracting(Object::toString).containsExactly(toEmail);
        }
    }

    // MimeMessageHelper(message, true, ...) wraps the text/html alternative part in outer
    // single-child multipart/mixed and multipart/related envelopes (to allow attachments/inline
    // resources), so it needs unwrapping down to the actual multipart/alternative part first.
    private String bodyPartContent(MimeMessage mimeMessage, int partIndex) throws Exception {
        var content = mimeMessage.getContent();
        while (content instanceof MimeMultipart multipart && multipart.getCount() == 1) {
            content = multipart.getBodyPart(0).getContent();
        }
        return (String) ((MimeMultipart) content).getBodyPart(partIndex).getContent();
    }
}
