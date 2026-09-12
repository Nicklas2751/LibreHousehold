package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.household.AccountRegistered;
import eu.wiegandt.librehousehold.household.AccountTokenIssuer;
import eu.wiegandt.librehousehold.household.VerificationDeletionWarningRequested;
import eu.wiegandt.librehousehold.household.VerificationEmailRequested;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reacts to account-registration/resend lifecycle events by issuing a fresh email verification
 * token and sending the verification email (see ADR-011: {@code household} publishes the event
 * without knowing that {@code notifications} exists; this listener is the sole consumer).
 */
@Component
public class AccountRegistrationListener {

    private final AccountTokenIssuer accountTokenIssuer;
    private final EmailSenderService emailSenderService;

    public AccountRegistrationListener(AccountTokenIssuer accountTokenIssuer, EmailSenderService emailSenderService) {
        this.accountTokenIssuer = accountTokenIssuer;
        this.emailSenderService = emailSenderService;
    }

    @ApplicationModuleListener
    public void on(AccountRegistered event) {
        issueTokenAndSendVerificationEmail(event.memberId(), event.email());
    }

    @ApplicationModuleListener
    public void on(VerificationEmailRequested event) {
        issueTokenAndSendVerificationEmail(event.memberId(), event.email());
    }

    @ApplicationModuleListener
    public void on(VerificationDeletionWarningRequested event) {
        emailSenderService.sendVerificationDeletionWarningEmail(event.email(), event.memberId());
    }

    private void issueTokenAndSendVerificationEmail(UUID memberId, String email) {
        var token = accountTokenIssuer.issueEmailVerificationToken(memberId);
        emailSenderService.sendVerificationEmail(email, memberId, token);
    }
}
