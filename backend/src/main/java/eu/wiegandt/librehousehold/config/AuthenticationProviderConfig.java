package eu.wiegandt.librehousehold.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The form login's {@link DaoAuthenticationProvider}, replacing Spring Security's default one so
 * that a failed login does not reveal whether an account exists (ENUM1 in Arc42 Chapter 8): the
 * e-mail verification is only checked after the password matched, so an unverified account with a
 * wrong password fails exactly like an unknown e-mail address. By default, Spring Security checks
 * {@link UserDetails#isEnabled()} before the password.
 *
 * <p>The lockout is still checked before the password, by the {@code UserDetailsService} itself,
 * so no password is compared during a lock.
 */
@Configuration
public class AuthenticationProviderConfig {

    @Bean
    DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                     PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPreAuthenticationChecks(AuthenticationProviderConfig::skipChecks);
        provider.setPostAuthenticationChecks(AuthenticationProviderConfig::requireEnabled);
        return provider;
    }

    // The account principal is never expired and its lock is enforced by the UserDetailsService
    private static void skipChecks(UserDetails user) {
    }

    private static void requireEnabled(UserDetails user) {
        if (!user.isEnabled()) {
            throw new DisabledException("User is disabled");
        }
    }
}
