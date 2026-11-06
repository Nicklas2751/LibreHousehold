package eu.wiegandt.librehousehold.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.web.session.SessionInformationExpiredEvent;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentSessionExpiredStrategyTest {

    @Nested
    class onExpiredSessionDetected {

        @Test
        void htmlAccept_redirectsToLogin() throws Exception {
            // given
            var response = new MockHttpServletResponse();

            // when
            new ConcurrentSessionExpiredStrategy().onExpiredSessionDetected(
                    expiredEvent(MediaType.TEXT_HTML_VALUE, response));

            // then
            assertThat(response.getRedirectedUrl()).endsWith("/login");
        }

        // The SPA's fetch sends */* by default; only an explicit browser navigation may be redirected
        @ParameterizedTest
        @ValueSource(strings = {MediaType.ALL_VALUE, MediaType.APPLICATION_JSON_VALUE})
        void nonHtmlAccept_returns401(String accept) throws Exception {
            // given
            var response = new MockHttpServletResponse();

            // when
            new ConcurrentSessionExpiredStrategy().onExpiredSessionDetected(expiredEvent(accept, response));

            // then
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    private SessionInformationExpiredEvent expiredEvent(String accept, MockHttpServletResponse response) {
        var request = new MockHttpServletRequest("GET", "/v1/me");
        request.addHeader(HttpHeaders.ACCEPT, accept);
        var sessionInformation = new SessionInformation("member", "session-id", new Date());
        return new SessionInformationExpiredEvent(sessionInformation, request, response);
    }
}
