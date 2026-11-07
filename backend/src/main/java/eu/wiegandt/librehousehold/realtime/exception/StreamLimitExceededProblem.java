package eu.wiegandt.librehousehold.realtime.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.time.Duration;

/**
 * A stream rejected because too many streams are open (RT5). The detail names no limit and no
 * count, so a client learns nothing about other sessions or the load of the server. {@code Retry-After}
 * carries the wait time in whole seconds, rounded up like {@code TooManyRequestsProblem}.
 */
public class StreamLimitExceededProblem extends ErrorResponseException {

    private static final URI TYPE = URI.create("/problems/too-many-streams");
    private static final String DETAIL = "Too many open event streams. Please try again later.";
    private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();

    public StreamLimitExceededProblem(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS);
        setType(TYPE);
        setDetail(DETAIL);
        getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.ceilDiv(retryAfter.toNanos(), NANOS_PER_SECOND)));
    }
}
