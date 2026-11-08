package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.LocalLoginProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.List;
import java.util.Set;

/**
 * Composition root of the single Spring Security filter chain (see ADR-013, ADR-014): form login
 * under {@code POST {basePath}/login}, {@code oauth2Login()} for external providers (ADR-016) and
 * logout under {@code POST {basePath}/logout}. The HTTP session is the only credential; the
 * session building blocks shared by every sign-in path live in {@link SessionManagementConfig}.
 *
 * <p>Lives in {@code config}, not in {@code household}: {@link AuthenticationProviderConfig} wires
 * the {@code household}-provided {@code UserDetailsService} into the {@code DaoAuthenticationProvider}
 * by type, so this class has no compile-time dependency on {@code household}'s internals (verified
 * by {@code ApplicationModules.verify()}, see {@code ApplicationTests}).
 *
 * <p>{@code @EnableMethodSecurity} activates {@code @PreAuthorize}, which the {@code *ApiDelegateImpl}
 * classes use together with {@code household.HouseholdAccessGuard} for per-household access control.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Shared with the rate limit key (see RateLimitingConfig): members log in with their e-mail address
    static final String LOGIN_EMAIL_PARAMETER = "email";
    private static final int UNLIMITED_SESSIONS = -1;
    private static final String LOGIN_PAGE = "/login";

    private final String basePath;

    SecurityConfig(@Value("${openapi.libreHousehold.base-path:/v1}") String basePath) {
        this.basePath = basePath;
    }

    /**
     * Origin allowlist for cookie-authenticated cross-origin requests (see CORS1 in Arc42 Chapter 8).
     * {@code allowCredentials(true)} is required for the session cookie to be sent at all, which is
     * why {@code allowedOrigins} must stay an explicit list — Spring rejects a wildcard origin
     * combined with credentials. Empty by default: the target deployment puts frontend and backend
     * behind the same Nginx reverse proxy (same-origin), so self-hosters serving them
     * from different origins must opt in explicitly via {@code librehousehold.security.cors.allowed-origins}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${librehousehold.security.cors.allowed-origins:}") List<String> allowedOrigins) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Keeps the provider's authorized client in the session instead of Spring Boot's in-memory
     * default, so it never outlives the session, even if the social-login callback could not
     * remove it (see {@link FederatedIdentityAuthenticationSuccessHandler}).
     */
    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    /**
     * Publishes every denied authorization as an {@code AuthorizationDeniedEvent}, so modules can log
     * rejected access to their endpoints (RT10 of the realtime stream, ADR-019). The filter chain
     * would publish request-level denials without this bean, but method security, i.e.
     * {@code @PreAuthorize}, publishes only when such a bean exists.
     */
    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringAuthorizationEventPublisher(applicationEventPublisher);
    }

    @Bean
    @ConditionalOnWebApplication(type = Type.SERVLET)
    SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http,
            LocalLoginProperties localLoginProperties,
            ObjectProvider<InMemoryClientRegistrationRepository> clientRegistrationRepository,
            SocialLoginRegistrationCheck socialLoginRegistrationCheck,
            FederatedIdentityAuthenticationSuccessHandler federatedIdentityAuthenticationSuccessHandler,
            ProblemDetailLoginFailureHandler loginFailureHandler,
            GitHubEmailClient gitHubEmailClient,
            SessionRegistry sessionRegistry,
            CsrfTokenRepository csrfTokenRepository,
            @Qualifier(SessionManagementConfig.CSRF_SESSION_AUTHENTICATION_STRATEGY)
            SessionAuthenticationStrategy csrfSessionAuthenticationStrategy,
            OncePerRequestFilter rateLimitingFilter,
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName) {
        http.authorizeHttpRequests(authorize -> authorize
                        // Spring Security authorizes every dispatch. An ASYNC dispatch only resumes a
                        // request whose REQUEST dispatch was already authorized, e.g. when a streamed
                        // response ends. By then the session may be gone (logout), and a denial could
                        // only fail on the committed response. ADR-019 binds a stream to its session
                        // through a session check while streaming, not through this re-authorization.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        // "/error" must stay public: Spring MVC forwards internally to it for any
                        // non-2xx response, and that forward is itself subject to these rules.
                        .requestMatchers("/error",
                                basePath + "/login",
                                basePath + "/household/setup",
                                basePath + "/invite/**",
                                basePath + "/members/availability",
                                basePath + "/members/verification/confirm",
                                basePath + "/password-reset/request",
                                basePath + "/password-reset/confirm",
                                basePath + "/auth/social-context",
                                basePath + "/auth/social-registration",
                                basePath + "/auth/discover")
                        .permitAll()
                        .anyRequest().access(new AccountPrincipalAuthorizationManager()))
                .cors(Customizer.withDefaults())
                // Nothing to resume after a login: the SPA navigates itself
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                // ADR-014: spa() resolves the raw token the SPA reads from the XSRF-TOKEN cookie. No
                // exception is needed for the unauthenticated POSTs (login, setup, join): the SPA
                // always gets the cookie from a preceding GET.
                .csrf(csrf -> csrf.spa()
                        .csrfTokenRepository(csrfTokenRepository)
                        .sessionAuthenticationStrategy(csrfSessionAuthenticationStrategy))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // RATE1: must reject before the form login filter authenticates
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        configureSessionManagement(http, sessionRegistry);
        if (localLoginProperties.enabled()) {
            configureFormLogin(http, loginFailureHandler);
        }
        var registrations = clientRegistrationRepository.getIfAvailable();
        if (registrations != null) {
            socialLoginRegistrationCheck.check(registrations);
            configureOAuth2Login(http, federatedIdentityAuthenticationSuccessHandler, gitHubEmailClient);
        }
        configureLogout(http, csrfTokenRepository, sessionCookieName);
        configureExceptionHandling(http);
        return http.build();
    }

    // The ConcurrentSessionFilter rejects sessions expired via the SessionRegistry, e.g. by a password reset
    private static void configureSessionManagement(HttpSecurity http, SessionRegistry sessionRegistry) {
        http.sessionManagement(session -> session
                .sessionConcurrency(concurrency -> concurrency
                        .maximumSessions(UNLIMITED_SESSIONS)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(new ConcurrentSessionExpiredStrategy())));
    }

    // Without form login, POST {basePath}/login reaches SessionApiDelegateImpl, which rejects it
    private void configureFormLogin(HttpSecurity http, ProblemDetailLoginFailureHandler loginFailureHandler) {
        http.formLogin(login -> login
                .loginPage(LOGIN_PAGE)
                .loginProcessingUrl(basePath + LOGIN_PAGE)
                .usernameParameter(LOGIN_EMAIL_PARAMETER)
                .successHandler((_, response, _) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
                .failureHandler(loginFailureHandler));
    }

    private static void configureOAuth2Login(HttpSecurity http,
                                             FederatedIdentityAuthenticationSuccessHandler successHandler,
                                             GitHubEmailClient gitHubEmailClient) {
        var userService = new GitHubEmailAwareOAuth2UserService(new DefaultOAuth2UserService(), gitHubEmailClient);
        http.oauth2Login(login -> login
                .loginPage(LOGIN_PAGE)
                .userInfoEndpoint(userInfo -> userInfo.userService(userService))
                .successHandler(successHandler)
                // E.g. the user cancels at the provider, which calls back with error=access_denied
                .failureUrl(FederatedIdentityAuthenticationSuccessHandler.SOCIAL_LOGIN_FAILURE_URL));
    }

    // Reading the deferred token after the logout handlers removed the old one issues a new XSRF-TOKEN
    private void configureLogout(HttpSecurity http, CsrfTokenRepository csrfTokenRepository, String sessionCookieName) {
        http.logout(logout -> logout
                .logoutUrl(basePath + "/logout")
                .deleteCookies(sessionCookieName)
                .logoutSuccessHandler((request, response, _) -> {
                    csrfTokenRepository.loadDeferredToken(request, response).get();
                    response.setStatus(HttpStatus.NO_CONTENT.value());
                }));
    }

    // Browser navigations (Accept: text/html) are redirected to the login page; API calls get a plain 401
    private static void configureExceptionHandling(HttpSecurity http) {
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint(LOGIN_PAGE),
                        browserNavigation())
                .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        AnyRequestMatcher.INSTANCE));
    }

    // fetch() sends Accept: */* by default, which would otherwise match text/html and redirect the SPA's API calls
    static MediaTypeRequestMatcher browserNavigation() {
        var matcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        matcher.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        return matcher;
    }
}
