package eu.wiegandt.librehousehold.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * RATE1 (Arc42 Chapter 8): rate-limiting thresholds for {@code POST /login}, keyed per IP only
 * (see {@link RateLimitingConfig}).
 */
@ConfigurationProperties(prefix = "librehousehold.security.rate-limit.login-per-ip")
public record LoginPerIpRateLimitProperties(int maxAttempts, Duration windowDuration) {
}
