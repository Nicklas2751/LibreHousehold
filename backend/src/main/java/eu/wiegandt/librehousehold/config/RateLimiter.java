package eu.wiegandt.librehousehold.config;

import java.time.Duration;
import java.util.Optional;

/**
 * Pure abstraction over the rate-limiting decision for a single key (e.g. a login
 * {@code username|ip} combination, or a client IP for the sensitive endpoints), extracted so it is
 * unit-testable without any Servlet mocking (see {@link RateLimitingFilter}, RATE1 in Arc42
 * Chapter 8).
 */
public interface RateLimiter {

    /**
     * Consumes one attempt for {@code key}.
     *
     * @return empty if the attempt is allowed, otherwise the time until the next attempt is allowed
     */
    Optional<Duration> acquire(String key);
}
