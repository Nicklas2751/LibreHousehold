package eu.wiegandt.librehousehold.household;

import org.springframework.security.authentication.LockedException;

import java.time.Instant;

/**
 * Thrown by {@code AccountUserDetailsService} when an account is temporarily locked (RATE1 in
 * Arc42 Chapter 8, see {@code AccountLockoutListener}). Carries {@link #getLockedUntil()} so
 * {@code ProblemDetailLoginFailureHandler} (in the {@code config} module) can tell the user when
 * they can try again - a deliberate, documented ENUM1 trade-off (Arc42 Chapter 8). Defined in the
 * public package so the consuming {@code config} module can reference the type (see ADR-011).
 */
public class AccountLockedException extends LockedException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("This account is temporarily locked due to repeated failed login attempts.");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
