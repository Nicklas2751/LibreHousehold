package eu.wiegandt.librehousehold.config;

import jakarta.servlet.ServletException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.io.IOException;

/**
 * Reuses the same browser-vs-API distinction as {@code SecurityConfig#defaultSecurityFilterChain}'s
 * {@code exceptionHandling()} (browser navigations redirect to {@code /login}, API/XHR calls get a
 * plain 401) for the {@link org.springframework.security.web.session.ConcurrentSessionFilter}, which
 * enforces the session expiry {@code PasswordResetService} sets on the {@code SessionRegistry}
 * (Bug A: without this filter, {@code SessionInformation.expireNow()} was never checked per request).
 */
class ConcurrentSessionExpiredStrategy implements SessionInformationExpiredStrategy {

    private final AuthenticationEntryPoint entryPoint = DelegatingAuthenticationEntryPoint.builder()
            .addEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML))
            .defaultEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            .build();

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException, ServletException {
        entryPoint.commence(event.getRequest(), event.getResponse(), new SessionAuthenticationException(
                "This session has expired, since it was invalidated via the SessionRegistry."));
    }
}
