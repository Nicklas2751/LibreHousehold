package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.PasswordReset;
import eu.wiegandt.librehousehold.household.exception.AccountTokenInvalidException;
import eu.wiegandt.librehousehold.household.exception.PasswordResetTokenInvalidException;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates the password-reset confirmation flow: resets the password and lifts a login lockout,
 * then ends all of the account's existing sessions (see RESET1 in Arc42 Chapter 8). Kept separate
 * from {@link AccountService} on purpose (SRP): {@code AccountService} stays free of session
 * concerns.
 */
@Service
public class PasswordResetService implements PasswordReset {

    private final MemberManagementService memberManagementService;
    private final AccountTokenService accountTokenService;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final MemberSessions memberSessions;

    public PasswordResetService(MemberManagementService memberManagementService,
                                 AccountTokenService accountTokenService,
                                 AccountService accountService,
                                 AccountRepository accountRepository,
                                 MemberSessions memberSessions) {
        this.memberManagementService = memberManagementService;
        this.accountTokenService = accountTokenService;
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.memberSessions = memberSessions;
    }

    @Override
    public void requestPasswordReset(String email) {
        memberManagementService.requestPasswordReset(email);
    }

    @Override
    @Transactional
    public void confirmPasswordReset(UUID token, String newPassword) {
        var memberId = consumePasswordResetToken(token);
        // A reset must never add a local password to a social-login-only account; answering like an
        // invalid token keeps the response indistinguishable from any other unusable link.
        if (!accountService.hasPassword(memberId)) {
            throw new PasswordResetTokenInvalidException();
        }

        accountService.resetPassword(memberId, newPassword);
        // Proving control of the mailbox lifts a lockout at once, so an attacker who keeps a known
        // address locked out (RATE1) cannot lock its owner out for good
        accountRepository.resetFailedLoginAttempts(memberId);

        memberSessions.expireAll(memberId);
    }

    private UUID consumePasswordResetToken(UUID token) {
        try {
            return accountTokenService.consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);
        } catch (AccountTokenInvalidException _) {
            throw new PasswordResetTokenInvalidException();
        }
    }
}
