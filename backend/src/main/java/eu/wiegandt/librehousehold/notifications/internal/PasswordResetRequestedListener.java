package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.household.AccountTokenIssuer;
import eu.wiegandt.librehousehold.household.PasswordResetRequested;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to password-reset requests by issuing a fresh, single-use password reset token and
 * sending the reset email (see ADR-011: {@code household} publishes the event without knowing
 * that {@code notifications} exists; this listener is the sole consumer).
 */
@Component
public class PasswordResetRequestedListener {

    private final AccountTokenIssuer accountTokenIssuer;
    private final EmailSenderService emailSenderService;

    public PasswordResetRequestedListener(AccountTokenIssuer accountTokenIssuer, EmailSenderService emailSenderService) {
        this.accountTokenIssuer = accountTokenIssuer;
        this.emailSenderService = emailSenderService;
    }

    @ApplicationModuleListener
    public void on(PasswordResetRequested event) {
        var token = accountTokenIssuer.issuePasswordResetToken(event.memberId());
        emailSenderService.sendPasswordResetEmail(event.email(), token);
    }
}
