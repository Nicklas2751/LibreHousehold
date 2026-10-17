package eu.wiegandt.librehousehold.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects the form login ({@code POST /login}) on instances that turned local login off, so hiding
 * the form in the client is not the only barrier. Redirects like a failed form login does, with a
 * dedicated reason the login page can show.
 */
class LocalLoginDisabledFilter extends OncePerRequestFilter {

    private static final String LOCAL_LOGIN_DISABLED_URL = "/login?error&reason=local-login-disabled";

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final RequestMatcher formLoginRequest = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (formLoginRequest.matches(request)) {
            redirectStrategy.sendRedirect(request, response, LOCAL_LOGIN_DISABLED_URL);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
