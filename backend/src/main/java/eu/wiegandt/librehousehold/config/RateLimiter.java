package eu.wiegandt.librehousehold.config;

/**
 * Pure abstraction over the rate-limiting decision for a single key (e.g. a login
 * {@code username|ip} combination, or a client IP for the sensitive endpoints), extracted so it is
 * unit-testable without any Servlet mocking (see {@link RateLimitingFilter}, RATE1 in Arc42
 * Chapter 8).
 */
public interface RateLimiter {

    boolean isRateLimited(String key);
}
