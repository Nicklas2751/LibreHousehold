package eu.wiegandt.librehousehold.household;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The only principal of an authenticated session, whichever way the member signed in (form login,
 * social login, or right after household setup / invite join, see ADR-013). Identified by the
 * immutable {@code memberId}: {@link #equals}/{@link #hashCode} compare nothing else, so the
 * {@code SessionRegistry} finds all sessions of a member via {@link #identifiedBy(UUID)}.
 *
 * <p>{@link #getUsername()} therefore returns the member ID, although members log in with their
 * e-mail address: the e-mail address may change, the member ID never does. The password hash is
 * only needed while the password is compared and is erased afterwards ({@link CredentialsContainer}),
 * so it never stays in the session.
 */
public final class AccountPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID memberId;
    private final @Nullable UUID householdId;
    private @Nullable String passwordHash;
    // Snapshot for isEnabled() at login only; the current state is read from the database (see GET /me)
    private final boolean emailVerified;

    public AccountPrincipal(UUID memberId, @Nullable UUID householdId, @Nullable String passwordHash,
                            boolean emailVerified) {
        this.memberId = memberId;
        this.householdId = householdId;
        this.passwordHash = passwordHash;
        this.emailVerified = emailVerified;
    }

    /**
     * A principal that only carries the member ID, e.g. to look up the member's sessions in the
     * {@code SessionRegistry}.
     */
    public static AccountPrincipal identifiedBy(UUID memberId) {
        return new AccountPrincipal(memberId, null, null, true);
    }

    public UUID memberId() {
        return memberId;
    }

    public @Nullable UUID householdId() {
        return householdId;
    }

    @Override
    public @NonNull String getUsername() {
        return memberId.toString();
    }

    @Override
    public @Nullable String getPassword() {
        return passwordHash;
    }

    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    /**
     * An unverified account is treated as disabled, blocking login until the e-mail address is
     * confirmed (see VERIFY1 in Arc42 Chapter 8).
     */
    @Override
    public boolean isEnabled() {
        return emailVerified;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AccountPrincipal principal && memberId.equals(principal.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId);
    }

    @Override
    public @NonNull String toString() {
        return "AccountPrincipal[memberId=" + memberId + "]";
    }
}
