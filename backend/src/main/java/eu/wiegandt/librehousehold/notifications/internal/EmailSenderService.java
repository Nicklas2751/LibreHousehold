package eu.wiegandt.librehousehold.notifications.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Encapsulates {@link JavaMailSender} for the whole {@code notifications} module. Kept as a single,
 * simple {@code SimpleMailMessage}-based service (no templating library): the mail contents here
 * are short and plain-text, so a dedicated templating dependency (e.g. Thymeleaf) would be a
 * speculative addition for no current benefit.
 */
@Service
public class EmailSenderService {

    private final JavaMailSender mailSender;
    private final String frontendBaseUrl;

    public EmailSenderService(JavaMailSender mailSender,
                               @Value("${librehousehold.frontend.base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public void sendVerificationEmail(String toEmail, UUID token) {
        var message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Verify your email address");
        message.setText("Please verify your email address by opening this link: " + verificationLink(token));
        mailSender.send(message);
    }

    public void sendPasswordResetEmail(String toEmail, UUID token) {
        var message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Reset your password");
        message.setText("Please reset your password by opening this link: " + passwordResetLink(token));
        mailSender.send(message);
    }

    public void sendVerificationDeletionWarningEmail(String toEmail) {
        var message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Your account will be deleted soon");
        message.setText("Your account's email address is still unverified. "
                + "Please verify it soon, otherwise your account will be deleted.");
        mailSender.send(message);
    }

    private String verificationLink(UUID token) {
        return frontendBaseUrl + "/verify-email/" + token;
    }

    private String passwordResetLink(UUID token) {
        return frontendBaseUrl + "/reset-password/" + token;
    }
}
