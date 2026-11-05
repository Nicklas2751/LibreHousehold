package eu.wiegandt.librehousehold.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingConfigTest {

    @Nested
    class loginKey {

        @Test
        void emailLongerThan254Chars_truncated() {
            // given
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("username", "a".repeat(300));
            var expected = "a".repeat(254) + "|127.0.0.1";

            // when
            var result = RateLimitingConfig.loginKey(request);

            // then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        void normalEmail_unchanged() {
            // given
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("127.0.0.1");
            request.setParameter("username", "max@example.com");

            // when
            var result = RateLimitingConfig.loginKey(request);

            // then
            assertThat(result).isEqualTo("max@example.com|127.0.0.1");
        }
    }
}
