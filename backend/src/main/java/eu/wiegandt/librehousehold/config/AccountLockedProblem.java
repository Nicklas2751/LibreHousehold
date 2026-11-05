package eu.wiegandt.librehousehold.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/**
 * A form login of a temporarily locked account (RATE1 in Arc42 Chapter 8). 403 instead of 423,
 * which is WebDAV-specific. {@code Retry-After} carries the remaining lock time in whole seconds,
 * rounded up like in {@link TooManyRequestsProblem}.
 */
class AccountLockedProblem extends ErrorResponseException {

    private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();

    AccountLockedProblem(Instant lockedUntil) {
        super(HttpStatus.FORBIDDEN);
        setType(URI.create("/problems/account-locked"));
        setDetail("This account is temporarily locked due to repeated failed login attempts.");
        getBody().setProperty("lockedUntil", lockedUntil);
        var remaining = Duration.between(Instant.now(), lockedUntil);
        getHeaders().set(HttpHeaders.RETRY_AFTER,
                String.valueOf(Math.max(0, Math.ceilDiv(remaining.toNanos(), NANOS_PER_SECOND))));
    }
}
