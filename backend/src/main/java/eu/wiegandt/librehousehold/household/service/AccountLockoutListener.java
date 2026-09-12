package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
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

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        findMemberId(event).ifPresent(this::registerFailedAttempt);
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        findMemberId(event).ifPresent(accountRepository::resetFailedLoginAttempts);
    }

    private void registerFailedAttempt(UUID memberId) {
        accountRepository.incrementFailedLoginAttempts(memberId);
        var failedAttempts = accountRepository.findById(memberId).map(AccountEntity::failedLoginAttempts).orElse(0);
        if (failedAttempts >= lockoutProperties.maxAttempts()) {
            accountRepository.lockUntil(memberId, Instant.now().plus(lockoutProperties.lockoutDuration()));
        }
    }

    private Optional<UUID> findMemberId(AbstractAuthenticationEvent event) {
        return memberManagementService.findMemberIdByEmail(event.getAuthentication().getName());
    }
}
