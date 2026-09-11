package eu.wiegandt.librehousehold.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Distinguishes an unverified account ({@link DisabledException}, see {@code AccountPrincipal#isEnabled()},
 * P2.2) from any other login failure (e.g. wrong password), so the frontend can show a "please
 * verify your email" hint instead of the generic "incorrect email or password" message. This is a
 * deliberate, already-documented ENUM1 trade-off (see the {@code DisabledException} footnote in
 * Arc42 Chapter 8), not a new design decision.
 *
 * <p>Delegates to two plain {@link SimpleUrlAuthenticationFailureHandler} instances instead of
 * building the redirect itself, to keep their other behavior (e.g. clearing any cached
 * authentication attributes) unchanged for both cases.
 */
@Component
public class UnverifiedAccountLoginFailureHandler implements AuthenticationFailureHandler {

    private final AuthenticationFailureHandler unverifiedAccountFailureHandler =
            new SimpleUrlAuthenticationFailureHandler("/login?error&reason=unverified");
    private final AuthenticationFailureHandler defaultFailureHandler =
            new SimpleUrlAuthenticationFailureHandler("/login?error");

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException, ServletException {
        var handler = exception instanceof DisabledException ? unverifiedAccountFailureHandler : defaultFailureHandler;
        handler.onAuthenticationFailure(request, response, exception);
    }
}
