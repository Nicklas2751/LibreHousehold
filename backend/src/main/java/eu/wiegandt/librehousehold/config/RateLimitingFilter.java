package eu.wiegandt.librehousehold.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Enforces every configured {@link RateLimitRule} (RATE1 in Arc42 Chapter 8), responding
 * {@code 429 Too Many Requests} once any matching rule's {@link RateLimiter} reports the request's
 * key as rate-limited. No distinction between browser navigation and API calls is needed here
 * (unlike {@code ConcurrentSessionExpiredStrategy}): 429 is a meaningful status for both.
 */
class RateLimitingFilter extends OncePerRequestFilter {

    private final List<RateLimitRule> rules;

    RateLimitingFilter(List<RateLimitRule> rules) {
        this.rules = rules;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (rules.stream().filter(rule -> rule.appliesTo(request)).anyMatch(rule -> rule.isRateLimited(request))) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        filterChain.doFilter(request, response);
    }
}
