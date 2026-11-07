package eu.wiegandt.librehousehold.realtime.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StreamLimitExceededProblemTest {

    @Test
    void new_retryAfter_tooManyStreamsProblemDetail() {
        // given
        var expected = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many open event streams. Please try again later.");
        expected.setType(URI.create("/problems/too-many-streams"));

        // when
        var problem = new StreamLimitExceededProblem(Duration.ofSeconds(30));

        // then
        assertThat(problem.getBody()).isEqualTo(expected);
    }

    @Test
    void new_fractionalRetryAfter_retryAfterHeaderRoundedUpToSeconds() {
        // when
        var problem = new StreamLimitExceededProblem(Duration.ofMillis(29_500));

        // then
        assertThat(problem.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
    }
}
