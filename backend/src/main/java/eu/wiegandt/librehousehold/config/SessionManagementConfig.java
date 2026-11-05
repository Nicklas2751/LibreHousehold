package eu.wiegandt.librehousehold.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.util.List;

/**
 * Session building blocks shared by every way of signing in (see ADR-013): form login and
 * {@code oauth2Login()} get them via the filter chain in {@link SecurityConfig}, the programmatic
 * sign-in after household setup / invite join ({@code household.service.AccountSessionSignIn}) via
 * the {@link Primary} {@link SessionAuthenticationStrategy}. Each path thereby renews the session
 * ID exactly once, registers the session in the {@link SessionRegistry} (so a password reset can
 * end it, RESET1 in Arc42 Chapter 8) and issues a new XSRF-TOKEN.
 *
 * <p>The filter chain must not get the {@code Primary} composite via
 * {@code sessionManagement().sessionAuthenticationStrategy(...)}: Spring Security would only put it
 * in front of its own defaults and append the CSRF strategy again, renewing the session ID twice
 * and removing the new token again.
 */
@Configuration
class SessionManagementConfig {

    static final String CSRF_SESSION_AUTHENTICATION_STRATEGY = "csrfSessionAuthenticationStrategy";

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Notifies {@link SessionRegistryImpl} of session lifecycle events (e.g. destruction), so it can
     * remove the corresponding {@code SessionInformation} entries.
     */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // ADR-014: the SPA reads XSRF-TOKEN from the cookie, so it must not be HttpOnly
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean(CSRF_SESSION_AUTHENTICATION_STRATEGY)
    SessionAuthenticationStrategy csrfSessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new CsrfAuthenticationStrategy(csrfTokenRepository), new CsrfTokenMaterializingStrategy()));
    }

    @Bean
    @Primary
    SessionAuthenticationStrategy sessionAuthenticationStrategy(
            SessionRegistry sessionRegistry,
            @Qualifier(CSRF_SESSION_AUTHENTICATION_STRATEGY) SessionAuthenticationStrategy csrfSessionAuthenticationStrategy) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry),
                csrfSessionAuthenticationStrategy));
    }
}
