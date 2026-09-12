package eu.wiegandt.librehousehold.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * RATE1 (Arc42 Chapter 8): rate-limiting thresholds for the password-reset/verification endpoints,
 * keyed per IP since they are partly unauthenticated (see {@link RateLimitingConfig}).
 */
@ConfigurationProperties(prefix = "librehousehold.security.rate-limit.sensitive-endpoints")
public record SensitiveEndpointsRateLimitProperties(int maxAttempts, Duration windowDuration) {
}
