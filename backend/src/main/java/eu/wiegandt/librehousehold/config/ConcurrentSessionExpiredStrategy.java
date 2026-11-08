package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import jakarta.servlet.ServletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

import java.io.IOException;

/**
 * Reuses the same browser-vs-API distinction as {@code SecurityConfig#defaultSecurityFilterChain}'s
 * {@code exceptionHandling()} (browser navigations redirect to {@code /login}, API/XHR calls get a
 * plain 401) for the {@link org.springframework.security.web.session.ConcurrentSessionFilter}, which
 * enforces the session expiry {@code PasswordResetService} sets on the {@code SessionRegistry}
 * (Bug A: without this filter, {@code SessionInformation.expireNow()} was never checked per request).
 *
 * <p>Logs every rejected request: the filter runs before the {@code AuthorizationFilter}, so no
 * {@code AuthorizationDeniedEvent} reports these rejections (RT10). The log line never contains the
 * session ID, since it is the credential of the session.
 */
class ConcurrentSessionExpiredStrategy implements SessionInformationExpiredStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentSessionExpiredStrategy.class);
    private static final String UNKNOWN_MEMBER = "unknown";

    private final AuthenticationEntryPoint entryPoint = DelegatingAuthenticationEntryPoint.builder()
            .addEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), SecurityConfig.browserNavigation())
            .defaultEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            .build();

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException, ServletException {
        LOG.warn("Request {} {} of member {} rejected: session expired", event.getRequest().getMethod(),
                event.getRequest().getRequestURI(), memberIdOf(event.getSessionInformation().getPrincipal()));
        entryPoint.commence(event.getRequest(), event.getResponse(), new SessionAuthenticationException(
                "This session has expired, since it was invalidated via the SessionRegistry."));
    }

    private static Object memberIdOf(Object principal) {
        return principal instanceof AccountPrincipal accountPrincipal ? accountPrincipal.memberId() : UNKNOWN_MEMBER;
    }
}
