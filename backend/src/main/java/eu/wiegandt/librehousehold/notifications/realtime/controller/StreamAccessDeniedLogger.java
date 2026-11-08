package eu.wiegandt.librehousehold.notifications.realtime.controller;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher.MatchResult;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Logs rejected attempts to open an event stream (RT10). Spring Security publishes an
 * {@link AuthorizationDeniedEvent} for every denial: on the request level, before a controller runs,
 * for a session that is not signed in (401); on the method level, from {@code @PreAuthorize}, for a
 * member of another household (403). Only denials of the stream are logged, other endpoints keep
 * their quiet behaviour.
 */
@Component
class StreamAccessDeniedLogger {

    private static final Logger LOG = LoggerFactory.getLogger(StreamAccessDeniedLogger.class);
    private static final String HOUSEHOLD_ID_VARIABLE = "householdId";
    private static final String UNKNOWN_MEMBER = "unknown";
    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    private final RequestMatcher streamPath;

    StreamAccessDeniedLogger(@Value("${openapi.libreHousehold.base-path:/v1}") String basePath) {
        streamPath = PathPatternRequestMatcher.pathPattern(HttpMethod.GET,
                basePath + HouseholdEventStreamController.STREAM_PATH);
    }

    @EventListener
    void logDenial(AuthorizationDeniedEvent<?> event) {
        requestOf(event.getObject())
                .map(streamPath::matcher)
                .filter(MatchResult::isMatch)
                .ifPresent(match -> logStreamDenial(event.getAuthentication().get(), match));
    }

    // On the method level the request is an argument of the controller method
    private static Optional<HttpServletRequest> requestOf(Object secured) {
        return switch (secured) {
            case HttpServletRequest request -> Optional.of(request);
            case MethodInvocation invocation -> Arrays.stream(invocation.getArguments())
                    .filter(HttpServletRequest.class::isInstance)
                    .map(HttpServletRequest.class::cast)
                    .findFirst();
            default -> Optional.empty();
        };
    }

    // Never the session ID: it is the credential of the session
    private static void logStreamDenial(Authentication authentication, MatchResult match) {
        LOG.warn("Event stream of member {} in household {} denied: {}", memberOf(authentication),
                match.getVariables().get(HOUSEHOLD_ID_VARIABLE), statusFor(authentication).value());
    }

    private static Object memberOf(Authentication authentication) {
        return authentication.getPrincipal() instanceof AccountPrincipal principal
                ? principal.memberId()
                : UNKNOWN_MEMBER;
    }

    // Mirrors the ExceptionTranslationFilter: a session that is not signed in gets 401, any other 403
    private static HttpStatus statusFor(Authentication authentication) {
        return TRUST_RESOLVER.isAuthenticated(authentication) ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
    }
}
