package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountTokenIssuer;
import eu.wiegandt.librehousehold.household.exception.AccountTokenInvalidException;
import eu.wiegandt.librehousehold.household.model.AccountTokenEntity;
import eu.wiegandt.librehousehold.household.repository.AccountTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountTokenService implements AccountTokenIssuer {

    public static final String EMAIL_VERIFICATION_PURPOSE = "EMAIL_VERIFICATION";
    public static final String PASSWORD_RESET_PURPOSE = "PASSWORD_RESET";

    private final AccountTokenRepository accountTokenRepository;
    private final Duration emailVerificationTokenValidity;
    private final Duration passwordResetTokenValidity;

    public AccountTokenService(
            AccountTokenRepository accountTokenRepository,
            @Value("${librehousehold.security.email-verification.token-validity}") Duration emailVerificationTokenValidity,
            @Value("${librehousehold.security.password-reset.token-validity}") Duration passwordResetTokenValidity) {
        this.accountTokenRepository = accountTokenRepository;
        this.emailVerificationTokenValidity = emailVerificationTokenValidity;
        this.passwordResetTokenValidity = passwordResetTokenValidity;
    }

    public UUID issueToken(UUID memberId, String purpose, Duration validity) {
        accountTokenRepository.deleteByMemberIdAndPurpose(memberId, purpose);
        var token = UUID.randomUUID();
        accountTokenRepository.save(new AccountTokenEntity(null, memberId, token, purpose, Instant.now().plus(validity)));
        return token;
    }

    public UUID consumeToken(UUID token, String purpose) {
        var accountToken = accountTokenRepository.findByTokenAndPurpose(token, purpose)
                .filter(candidate -> candidate.validUntil().isAfter(Instant.now()))
                .orElseThrow(AccountTokenInvalidException::new);
        accountTokenRepository.deleteById(accountToken.id());
        return accountToken.memberId();
    }

    @Override
    public UUID issueEmailVerificationToken(UUID memberId) {
        return issueToken(memberId, EMAIL_VERIFICATION_PURPOSE, emailVerificationTokenValidity);
    }

    @Override
    public UUID issuePasswordResetToken(UUID memberId) {
        return issueToken(memberId, PASSWORD_RESET_PURPOSE, passwordResetTokenValidity);
    }
}
