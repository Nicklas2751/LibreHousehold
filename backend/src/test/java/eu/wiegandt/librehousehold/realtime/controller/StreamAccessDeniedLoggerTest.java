package eu.wiegandt.librehousehold.realtime.controller;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.realtime.LogCapture;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.util.SimpleMethodInvocation;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StreamAccessDeniedLoggerTest {

    private static final String BASE_PATH = "/v1";

    @Test
    void logDenial_anonymousRequestToStream_warnWithHouseholdAndUnauthorized() {
        // given
        var logger = new StreamAccessDeniedLogger(BASE_PATH);
        var householdId = UUID.randomUUID();
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        var event = new AuthorizationDeniedEvent<>(() -> anonymous, streamRequest(householdId),
                new AuthorizationDecision(false));

        try (var log = LogCapture.of(StreamAccessDeniedLogger.class)) {
            // when
            logger.logDenial(event);

            // then
            assertThat(log.lines()).containsExactly(
                    "WARN Event stream of member unknown in household " + householdId + " denied: 401");
        }
    }

    @Test
    void logDenial_memberInvokingStreamMethod_warnWithMemberHouseholdAndForbidden() throws NoSuchMethodException {
        // given
        var logger = new StreamAccessDeniedLogger(BASE_PATH);
        var householdId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        var principal = AccountPrincipal.identifiedBy(memberId);
        var member = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        var openStream = HouseholdEventStreamController.class.getDeclaredMethod("openStream", UUID.class,
                AccountPrincipal.class, HttpServletRequest.class);
        var invocation = new SimpleMethodInvocation(null, openStream, householdId, principal,
                streamRequest(householdId));
        var event = new AuthorizationDeniedEvent<>(() -> member, invocation, new AuthorizationDecision(false));

        try (var log = LogCapture.of(StreamAccessDeniedLogger.class)) {
            // when
            logger.logDenial(event);

            // then
            assertThat(log.lines()).containsExactly(
                    "WARN Event stream of member " + memberId + " in household " + householdId + " denied: 403");
        }
    }

    @Test
    void logDenial_requestToOtherEndpoint_nothingLogged() {
        // given
        var logger = new StreamAccessDeniedLogger(BASE_PATH);
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        var event = new AuthorizationDeniedEvent<>(() -> anonymous,
                new MockHttpServletRequest("GET", BASE_PATH + "/household/" + UUID.randomUUID() + "/tasks"),
                new AuthorizationDecision(false));

        try (var log = LogCapture.of(StreamAccessDeniedLogger.class)) {
            // when
            logger.logDenial(event);

            // then
            assertThat(log.lines()).isEmpty();
        }
    }

    @Test
    void logDenial_memberInvokingOtherMethod_nothingLogged() throws NoSuchMethodException {
        // given
        var logger = new StreamAccessDeniedLogger(BASE_PATH);
        var member = UsernamePasswordAuthenticationToken.authenticated(
                AccountPrincipal.identifiedBy(UUID.randomUUID()), null, List.of());
        // Like the generated API delegates, which take no request argument
        var invocation = new SimpleMethodInvocation(null, Object.class.getMethod("toString"));
        var event = new AuthorizationDeniedEvent<>(() -> member, invocation, new AuthorizationDecision(false));

        try (var log = LogCapture.of(StreamAccessDeniedLogger.class)) {
            // when
            logger.logDenial(event);

            // then
            assertThat(log.lines()).isEmpty();
        }
    }

    private MockHttpServletRequest streamRequest(UUID householdId) {
        return new MockHttpServletRequest("GET", BASE_PATH + "/household/" + householdId + "/events");
    }
}
