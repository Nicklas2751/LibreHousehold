package eu.wiegandt.librehousehold.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import eu.wiegandt.librehousehold.household.AccountPrincipal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.web.session.SessionInformationExpiredEvent;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

class ConcurrentSessionExpiredStrategyTest {

    private static final String SESSION_ID = "secret-session-id";
    private static final String STREAM_PATH = "/v1/household/9b2f3c1e-0d4a-4f7b-8c6e-2a1d5e7f9b30/events";

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

        @Test
        void sessionOfMember_warnWithMethodPathAndMember() throws Exception {
            // given
            var memberId = UUID.randomUUID();

            // when
            var warnings = loggedWarnings(expiredEvent("GET", STREAM_PATH, AccountPrincipal.identifiedBy(memberId)));

            // then
            assertThat(warnings).singleElement(STRING).contains("GET", STREAM_PATH, memberId.toString());
        }

        @Test
        void principalWithoutMember_warnWithUnknownMember() throws Exception {
            // when
            var warnings = loggedWarnings(expiredEvent("POST", "/v1/logout", "anonymous"));

            // then
            assertThat(warnings).singleElement(STRING).contains("POST", "/v1/logout", "member unknown");
        }

        // The session ID is the credential of the session and must never reach a log (RT10)
        @Test
        void sessionOfMember_sessionIdNotLogged() throws Exception {
            // when
            var warnings = loggedWarnings(
                    expiredEvent("GET", STREAM_PATH, AccountPrincipal.identifiedBy(UUID.randomUUID())));

            // then
            assertThat(warnings).noneMatch(warning -> warning.contains(SESSION_ID));
        }
    }

    private SessionInformationExpiredEvent expiredEvent(String accept, MockHttpServletResponse response) {
        var request = new MockHttpServletRequest("GET", "/v1/me");
        request.addHeader(HttpHeaders.ACCEPT, accept);
        var sessionInformation = new SessionInformation("member", SESSION_ID, new Date());
        return new SessionInformationExpiredEvent(sessionInformation, request, response);
    }

    private SessionInformationExpiredEvent expiredEvent(String method, String path, Object principal) {
        return new SessionInformationExpiredEvent(new SessionInformation(principal, SESSION_ID, new Date()),
                new MockHttpServletRequest(method, path), new MockHttpServletResponse());
    }

    // Captures only the lines written while the strategy runs; detaching keeps other tests unaffected
    private List<String> loggedWarnings(SessionInformationExpiredEvent event) throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(ConcurrentSessionExpiredStrategy.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            new ConcurrentSessionExpiredStrategy().onExpiredSessionDetected(event);
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream()
                .filter(loggingEvent -> loggingEvent.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
