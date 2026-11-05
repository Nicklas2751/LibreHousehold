package eu.wiegandt.librehousehold.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.time.Duration;

/**
 * A request rejected by {@link RateLimitingFilter} (RATE1 in Arc42 Chapter 8). {@code Retry-After}
 * carries the wait time in whole seconds, rounded up so a client that waits exactly that long is
 * never rejected again by the same window.
 */
class TooManyRequestsProblem extends ErrorResponseException {

    private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();

    TooManyRequestsProblem(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS);
        setType(URI.create("/problems/too-many-requests"));
        setDetail("Too many requests. Please try again later.");
        getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.ceilDiv(retryAfter.toNanos(), NANOS_PER_SECOND)));
    }
}
