package eu.wiegandt.librehousehold.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security defers loading the {@link CsrfToken} until something actually reads it (e.g. a
 * server-rendered form field, see {@code CsrfFilter}) — a pure-JSON SPA never does that, so the
 * {@code XSRF-TOKEN} cookie configured via {@code CsrfConfigurer#spa()} would otherwise only ever
 * appear on requests that happen to need it for other reasons (see ADR-014, ASVS SES2: the SPA must
 * always be able to read a current token). Resolving the token here on every request forces the
 * cookie repository to write it, independent of whether anything else consumes it.
 */
class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        var csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
