package eu.wiegandt.librehousehold.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class Bucket4jRateLimiterTest {

    @Nested
    class isRateLimited {

        @Test
        void underThreshold_returnsFalse() {
            // given
            var rateLimiter = new Bucket4jRateLimiter(2, Duration.ofMinutes(1));
            var key = "max@example.com|127.0.0.1";

            // when
            rateLimiter.isRateLimited(key);
            var result = rateLimiter.isRateLimited(key);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void atThreshold_returnsTrue() {
            // given
            var rateLimiter = new Bucket4jRateLimiter(2, Duration.ofMinutes(1));
            var key = "max@example.com|127.0.0.1";

            // when
            rateLimiter.isRateLimited(key);
            rateLimiter.isRateLimited(key);
            var result = rateLimiter.isRateLimited(key);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void afterWindowExpiry_returnsFalseAgain() throws InterruptedException {
            // given
            var windowDuration = Duration.ofMillis(200);
            var rateLimiter = new Bucket4jRateLimiter(1, windowDuration);
            var key = "max@example.com|127.0.0.1";
            rateLimiter.isRateLimited(key);

            // when
            Thread.sleep(windowDuration.plusMillis(300));
            var result = rateLimiter.isRateLimited(key);

            // then
            assertThat(result).isFalse();
        }
    }
}
