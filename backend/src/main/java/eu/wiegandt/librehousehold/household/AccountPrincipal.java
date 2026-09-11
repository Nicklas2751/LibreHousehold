package eu.wiegandt.librehousehold.household;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * {@link UserDetails} for the internal login against the embedded Authorization Server
 * (see ADR-013/DD-6). Not used for authorizing business API calls — that happens via
 * {@code AccountOidcPrincipal} once the OIDC login has completed (see P1.4).
 */
public record AccountPrincipal(String email, String passwordHash, boolean emailVerified) implements UserDetails {

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * An unverified account is treated as disabled, blocking login until the email is confirmed
     * (see the {@code VERIFY1}/{@code ENUM1} security controls in Arc42 Chapter 8): Spring
     * Security's {@code DefaultPreAuthenticationChecks} rejects the login with a
     * {@code DisabledException} before the password is even compared.
     */
    @Override
    public boolean isEnabled() {
        return emailVerified;
    }

    @Override
    public @NonNull String toString() {
        return "AccountPrincipal[email=" + email + "]";
    }
}
