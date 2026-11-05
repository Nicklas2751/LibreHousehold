package eu.wiegandt.librehousehold.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * One rate-limited endpoint group: which requests it applies to ({@code requestMatcher}), which
 * {@link RateLimiter} enforces it, and how to derive the rate-limit key from the request (e.g.
 * username+IP for login, plain IP for the sensitive endpoints).
 */
record RateLimitRule(RequestMatcher requestMatcher, RateLimiter rateLimiter, Function<HttpServletRequest, String> keyExtractor) {

    boolean appliesTo(HttpServletRequest request) {
        return requestMatcher.matches(request);
    }

    Optional<Duration> acquire(HttpServletRequest request) {
        return rateLimiter.acquire(keyExtractor.apply(request));
    }
}
