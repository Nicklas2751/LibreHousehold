package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Account-wide lockout after repeated failed login attempts, independent of how slowly the
 * attempts were made (see RATE1 in Arc42 Chapter 8, and {@link AccountUserDetailsService} for the
 * enforcement side). Listens to Spring Security's own authentication events - no new eventing
 * mechanism - published by the {@code DaoAuthenticationProvider} used for {@code POST /login}.
 */
@Component
@EnableConfigurationProperties(LockoutProperties.class)
public class AccountLockoutListener {

    private final MemberManagementService memberManagementService;
    private final AccountRepository accountRepository;
    private final LockoutProperties lockoutProperties;

    public AccountLockoutListener(MemberManagementService memberManagementService, AccountRepository accountRepository,
                                   LockoutProperties lockoutProperties) {
        this.memberManagementService = memberManagementService;
        this.accountRepository = accountRepository;
        this.lockoutProperties = lockoutProperties;
    }

    // Only a password can be guessed; a failure for an account without one is a login with an unknown e-mail address
    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        findMemberId(event)
                .flatMap(accountRepository::findById)
                .filter(AccountLockoutListener::hasPassword)
                .map(AccountEntity::memberId)
                .ifPresent(this::registerFailedAttempt);
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof AccountPrincipal accountPrincipal) {
            accountRepository.resetFailedLoginAttempts(accountPrincipal.memberId());
        }
    }

    private void registerFailedAttempt(UUID memberId) {
        accountRepository.incrementFailedLoginAttempts(memberId);
        var failedAttempts = accountRepository.findById(memberId).map(AccountEntity::failedLoginAttempts).orElse(0);
        // Intended: the counter survives an expired lock, so every further failure locks again; only
        // a successful login or a password reset clears it (RATE1 in Arc42 Chapter 8)
        if (failedAttempts >= lockoutProperties.maxAttempts()) {
            accountRepository.lockUntil(memberId, Instant.now().plus(lockoutProperties.lockoutDuration()));
        }
    }

    private static boolean hasPassword(AccountEntity account) {
        return account.passwordHash() != null;
    }

    // A failed attempt carries only the e-mail address the user typed in
    private Optional<UUID> findMemberId(AuthenticationFailureBadCredentialsEvent event) {
        return memberManagementService.findMemberIdByEmail(event.getAuthentication().getName());
    }
}
