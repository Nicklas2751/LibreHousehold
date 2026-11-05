package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountLockedException;
import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * The only place that constructs a full {@link AccountPrincipal}: for the form login by e-mail
 * address, and for the sign-ins without password by member ID (see {@code AccountSessionSignIn}).
 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;
    private final AccountRepository accountRepository;

    public AccountUserDetailsService(MemberRepository memberRepository, AccountRepository accountRepository) {
        this.memberRepository = memberRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public @NonNull AccountPrincipal loadUserByUsername(@NonNull String email) {
        var member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No member for this e-mail address"));
        var account = findAccount(member);
        requireNotLocked(account);
        return toPrincipal(member, account);
    }

    /**
     * Deliberately checks neither the lockout nor {@link AccountPrincipal#isEnabled()}: the callers
     * sign in a member who just registered (always unverified at that point) or who just
     * authenticated at an external provider, so no password guessing is involved.
     */
    public AccountPrincipal loadUserByMemberId(UUID memberId) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new UsernameNotFoundException("No member with this ID"));
        return toPrincipal(member, findAccount(member));
    }

    private AccountEntity findAccount(MemberEntity member) {
        return accountRepository.findById(member.id())
                .orElseThrow(() -> new UsernameNotFoundException("No account for this member"));
    }

    private AccountPrincipal toPrincipal(MemberEntity member, AccountEntity account) {
        return new AccountPrincipal(member.id(), member.householdId(), account.passwordHash(), account.emailVerified());
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
