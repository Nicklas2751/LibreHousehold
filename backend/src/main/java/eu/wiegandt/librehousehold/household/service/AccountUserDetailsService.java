package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountLockedException;
import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final MemberManagementService memberManagementService;
    private final AccountRepository accountRepository;

    public AccountUserDetailsService(MemberManagementService memberManagementService, AccountRepository accountRepository) {
        this.memberManagementService = memberManagementService;
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(@NonNull String email) {
        var memberId = memberManagementService.findMemberIdByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        var account = accountRepository.findById(memberId)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        requireNotLocked(account);
        return new AccountPrincipal(email, account.passwordHash(), account.emailVerified());
    }

    /**
     * Lockout after repeated failed login attempts (see {@code AccountLockoutListener}), on top of
     * the RATE1 rate-limiting (RATE1 in Arc42 Chapter 8) - independent of how slowly the attempts
     * were made. Checked eagerly here rather than via {@code AccountPrincipal#isAccountNonLocked()}
     * so it applies before the password is even compared.
     */
    private void requireNotLocked(AccountEntity account) {
        if (account.lockedUntil() != null && account.lockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException(account.lockedUntil());
        }
    }
}
