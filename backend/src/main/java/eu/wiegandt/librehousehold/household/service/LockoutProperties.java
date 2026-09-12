package eu.wiegandt.librehousehold.household.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * RATE1 (Arc42 Chapter 8): account-wide lockout thresholds after repeated failed login attempts,
 * enforced by {@link AccountLockoutListener}/{@link AccountUserDetailsService}.
 */
@ConfigurationProperties(prefix = "librehousehold.security.lockout")
public record LockoutProperties(int maxAttempts, Duration lockoutDuration) {
}
