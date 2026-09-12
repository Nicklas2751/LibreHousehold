package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.usersettings.PreferencesQuery;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Encapsulates {@link JavaMailSender} for the whole {@code notifications} module. Builds
 * multipart/alternative (text + HTML) messages via {@link MimeMessageHelper}, resolving translated
 * subject/body texts through {@link MessageSource} and the HTML layout through Thymeleaf's
 * {@link SpringTemplateEngine} (both configured standalone in {@link EmailTemplatingConfig}). The
 * recipient's language preference is looked up per send via {@link PreferencesQuery} (see ADR-011:
 * {@code notifications} is the sole module allowed to depend on both {@code household} and
 * {@code usersettings}).
 */
@Service
public class EmailSenderService {

    private static final String BODY_KEY_SUFFIX = ".body";
    private static final String VERIFICATION_TEMPLATE = "verification";
    private static final String PASSWORD_RESET_TEMPLATE = "password-reset";
    private static final String VERIFICATION_DELETION_WARNING_TEMPLATE = "verification-deletion-warning";
    private static final String HOUSEHOLD_DELETED_TEMPLATE = "household-deleted";
    private static final String MEMBER_REMOVED_TEMPLATE = "member-removed";

    private final JavaMailSender mailSender;
    private final MessageSource emailMessageSource;
    private final SpringTemplateEngine emailTemplateEngine;
    private final PreferencesQuery preferencesQuery;
    private final String frontendBaseUrl;

    public EmailSenderService(JavaMailSender mailSender, MessageSource emailMessageSource,
            SpringTemplateEngine emailTemplateEngine, PreferencesQuery preferencesQuery,
            @Value("${librehousehold.frontend.base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.emailMessageSource = emailMessageSource;
        this.emailTemplateEngine = emailTemplateEngine;
        this.preferencesQuery = preferencesQuery;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public void sendVerificationEmail(String toEmail, UUID memberId, UUID token) {
        var verificationLink = verificationLink(token);
        buildAndSend(toEmail, memberId, VERIFICATION_TEMPLATE, "verification.subject",
                new Object[] {verificationLink}, Map.of("verificationLink", verificationLink));
    }

    public void sendPasswordResetEmail(String toEmail, UUID memberId, UUID token) {
        var passwordResetLink = passwordResetLink(token);
        buildAndSend(toEmail, memberId, PASSWORD_RESET_TEMPLATE, "password-reset.subject",
                new Object[] {passwordResetLink}, Map.of("passwordResetLink", passwordResetLink));
    }

    public void sendVerificationDeletionWarningEmail(String toEmail, UUID memberId) {
        buildAndSend(toEmail, memberId, VERIFICATION_DELETION_WARNING_TEMPLATE,
                "verification-deletion-warning.subject", new Object[0], Map.of());
    }

    public void sendHouseholdDeletedEmail(String toEmail, UUID memberId, String memberName, String householdName) {
        buildAndSend(toEmail, memberId, HOUSEHOLD_DELETED_TEMPLATE, "household-deleted.subject",
                new Object[] {memberName, householdName}, Map.of("memberName", memberName, "householdName", householdName));
    }

    public void sendMemberRemovedEmail(String toEmail, UUID memberId, String memberName, String householdName) {
        buildAndSend(toEmail, memberId, MEMBER_REMOVED_TEMPLATE, "member-removed.subject",
                new Object[] {memberName, householdName}, Map.of("memberName", memberName, "householdName", householdName));
    }

    // messageArgs are positional (MessageFormat-style {0}, {1}, ...) for the plain-text body
    // resolved via MessageSource, while templateVariables are name-bound for the Thymeleaf HTML
    // template. They are passed separately rather than deriving one from the other's Map, because
    // Map.of(...) with two or more entries does not guarantee a stable iteration order, which
    // would let {0}/{1} placeholders silently swap between subject/body renders.
    void buildAndSend(String toEmail, UUID memberId, String templateName, String subjectKey,
            Object[] messageArgs, Map<String, Object> templateVariables) {
        var locale = resolveLocale(memberId);
        var subject = emailMessageSource.getMessage(subjectKey, null, locale);
        var plainText = emailMessageSource.getMessage(templateName + BODY_KEY_SUFFIX, messageArgs, locale);
        var htmlBody = emailTemplateEngine.process(templateName, new Context(locale, templateVariables));

        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(plainText, htmlBody);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            // Only thrown by MimeMessageHelper for malformed input (e.g. an invalid recipient
            // address), which cannot happen for already-validated member emails - rethrown
            // unchecked so callers (event listeners) don't need to declare it.
            throw new MailPreparationException(e);
        }
    }

    Locale resolveLocale(UUID memberId) {
        return preferencesQuery.getPreferencesOrDefault(memberId)
                .getLanguage()
                .map(language -> Locale.forLanguageTag(language.getValue()))
                .orElse(Locale.ENGLISH);
    }

    String verificationLink(UUID token) {
        return frontendBaseUrl + "/verify-email/" + token;
    }

    String passwordResetLink(UUID token) {
        return frontendBaseUrl + "/reset-password/" + token;
    }
}
