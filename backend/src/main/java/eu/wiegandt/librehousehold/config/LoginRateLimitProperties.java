package eu.wiegandt.librehousehold.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * RATE1 (Arc42 Chapter 8): rate-limiting thresholds for {@code POST /login}, keyed per
 * username+IP (see {@link RateLimitingConfig}).
 */
@ConfigurationProperties(prefix = "librehousehold.security.rate-limit.login")
public record LoginRateLimitProperties(int maxAttempts, Duration windowDuration) {
}
