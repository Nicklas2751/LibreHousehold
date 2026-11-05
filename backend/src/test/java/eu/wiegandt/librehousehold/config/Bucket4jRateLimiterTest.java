package eu.wiegandt.librehousehold.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class Bucket4jRateLimiterTest {

    @Nested
    class acquire {

        @Test
        void belowLimit_empty() {
            // given
            var rateLimiter = new Bucket4jRateLimiter(2, Duration.ofMinutes(1));
            var key = "max@example.com|127.0.0.1";

            // when
            rateLimiter.acquire(key);
            var result = rateLimiter.acquire(key);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void aboveLimit_returnsWaitTime() {
            // given
            var windowDuration = Duration.ofMinutes(1);
            var rateLimiter = new Bucket4jRateLimiter(2, windowDuration);
            var key = "max@example.com|127.0.0.1";

            // when
            rateLimiter.acquire(key);
            rateLimiter.acquire(key);
            var result = rateLimiter.acquire(key);

            // then
            assertThat(result).hasValueSatisfying(waitTime ->
                    assertThat(waitTime).isPositive().isLessThanOrEqualTo(windowDuration));
        }

        @Test
        void afterWindowExpiry_emptyAgain() throws InterruptedException {
            // given
            var windowDuration = Duration.ofMillis(200);
            var rateLimiter = new Bucket4jRateLimiter(1, windowDuration);
            var key = "max@example.com|127.0.0.1";
            rateLimiter.acquire(key);

            // when
            Thread.sleep(windowDuration.plusMillis(300));
            var result = rateLimiter.acquire(key);

            // then
            assertThat(result).isEmpty();
        }
    }
}
