package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.AccountLockedException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Distinguishes an unverified account ({@link DisabledException}, see {@code AccountPrincipal#isEnabled()},
 * P2.2) and a locked account ({@link AccountLockedException}, see RATE1/{@code AccountLockoutListener})
 * from any other login failure (e.g. wrong password), so the frontend can show a specific hint
 * instead of the generic "incorrect email or password" message. This is a deliberate, already
 * documented ENUM1 trade-off (see the {@code DisabledException} footnote in Arc42 Chapter 8,
 * extended to cover the lockout case too), not a new design decision.
 *
 * <p>Delegates to plain {@link SimpleUrlAuthenticationFailureHandler} instances for the two static
 * cases instead of building the redirect itself, to keep their other behavior (e.g. clearing any
 * cached authentication attributes) unchanged. The locked case needs a dynamic {@code lockedUntil}
 * query parameter, so it is redirected directly via {@link RedirectStrategy}.
 *
 * <p>{@code AccountLockedException} is thrown directly from {@code AccountUserDetailsService
 * #loadUserByUsername}, not via {@code UserDetails#isAccountNonLocked()} (unlike {@code DisabledException},
 * which the framework's own {@code DefaultPreAuthenticationChecks} throws itself based on
 * {@code AccountPrincipal#isEnabled()} - that path would only ever construct a bare, dataless
 * {@code LockedException}, never our subclass, so it cannot carry {@code lockedUntil}).
 * {@code DaoAuthenticationProvider#retrieveUser} wraps any exception thrown from
 * {@code loadUserByUsername} other than {@code UsernameNotFoundException} into a fresh
 * {@code InternalAuthenticationServiceException}, preserving the original as {@code getCause()} -
 * so {@code AccountLockedException} arrives here as that cause, not as {@code exception} itself.
 */
@Component
public class UnverifiedAccountLoginFailureHandler implements AuthenticationFailureHandler {

    private final AuthenticationFailureHandler unverifiedAccountFailureHandler =
            new SimpleUrlAuthenticationFailureHandler("/login?error&reason=unverified");
    private final AuthenticationFailureHandler defaultFailureHandler =
            new SimpleUrlAuthenticationFailureHandler("/login?error");
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException, ServletException {
        if (findAccountLockedException(exception) instanceof AccountLockedException lockedException) {
            var lockedUntil = URLEncoder.encode(lockedException.getLockedUntil().toString(), StandardCharsets.UTF_8);
            redirectStrategy.sendRedirect(request, response, "/login?error&reason=locked&lockedUntil=" + lockedUntil);
            return;
        }
        var handler = exception instanceof DisabledException ? unverifiedAccountFailureHandler : defaultFailureHandler;
        handler.onAuthenticationFailure(request, response, exception);
    }

    private static AccountLockedException findAccountLockedException(AuthenticationException exception) {
        return switch (exception) {
            case AccountLockedException direct -> direct;
            default -> exception.getCause() instanceof AccountLockedException cause ? cause : null;
        };
    }
}
