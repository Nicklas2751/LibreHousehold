package eu.wiegandt.librehousehold.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Composition root for the Spring Security / Spring Authorization Server wiring (see ADR-013,
 * ADR-014). Lives in its own {@code config} package, not in {@code core} or {@code household}: an
 * {@code household}-internal {@code UserDetailsService}/{@code PasswordEncoder}-based
 * {@code DaoAuthenticationProvider} is wired automatically by Spring Security's autoconfiguration
 * (type-based, no explicit reference here), which would otherwise be a forbidden
 * {@code core -> household} dependency (see the "Module Dependency Direction" rule in Arc42
 * Chapter 5). The {@code household}-provided {@link OidcUserService} bean (see
 * {@code AccountOidcUserService}) is injected here only by its framework-owned supertype, not by
 * its {@code household}-internal concrete type, so this class itself has no compile-time
 * dependency on {@code household} either — verified by Spring Modulith's
 * {@code ApplicationModules.verify()} (see {@code ApplicationTests}).
 *
 * <p>{@code @EnableMethodSecurity} activates {@code @PreAuthorize} support (enabled by default
 * alongside it), which the various {@code *ApiDelegateImpl} classes use together with
 * {@code household.HouseholdAccessGuard} for per-household/role access control (P1.6, DD-8).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
        return new JdbcRegisteredClientRepository(jdbcOperations);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(
            @Value("${librehousehold.security.oauth2-authorization-server.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    /**
     * The backend registers itself as an OAuth2 client of its own, embedded Authorization Server
     * (see DD-7). Endpoints are built from the configured issuer instead of relying on OIDC
     * issuer-discovery ({@code ClientRegistrations.fromIssuerLocation}): that call happens eagerly
     * during context refresh, before the embedded servlet container accepts requests, which would
     * make the authorization server unreachable from its own client at startup.
     *
     * <p>{@code authorizationUri} is deliberately decoupled from {@code issuer} instead of being
     * built as {@code issuer + "/oauth2/authorize"} like the other three: it is the only one of the
     * four URIs a real browser navigates to. In local development the frontend dev server proxies
     * the browser-facing endpoints to a different origin than the backend's own, so this URI must
     * reflect that proxy origin, while {@code tokenUri}/{@code jwkSetUri}/{@code userInfoUri} are
     * called exclusively server-to-server by this backend itself and must keep resolving directly
     * against {@code issuer} — routing those internal calls through the dev proxy as well-made the
     * backend proxy a request back to itself, hanging the code-exchange step indefinitely.
     */
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${librehousehold.security.oauth2-authorization-server.issuer}") String issuer,
            @Value("${librehousehold.security.oauth2-client.authorization-uri:${librehousehold.security.oauth2-authorization-server.issuer}/oauth2/authorize}") String authorizationUri,
            @Value("${librehousehold.security.oauth2-client.redirect-uri}") String redirectUri,
            @Value("${librehousehold.security.oauth2-client.client-secret}") String clientSecret) {
        var clientRegistration = ClientRegistration.withRegistrationId(RegisteredClientSeeder.CLIENT_ID)
                .clientId(RegisteredClientSeeder.CLIENT_ID)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope(OidcScopes.OPENID)
                .authorizationUri(authorizationUri)
                .tokenUri(issuer + "/oauth2/token")
                .jwkSetUri(issuer + "/oauth2/jwks")
                .userInfoUri(issuer + "/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName(RegisteredClientSeeder.CLIENT_ID)
                .build();
        return new InMemoryClientRegistrationRepository(clientRegistration);
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

    @Bean
    @Order(1)
    @ConditionalOnWebApplication(type = Type.SERVLET)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) {
        var authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, (as) -> as.oidc(Customizer.withDefaults()))
                // Required even though every request in this chain must be authenticated anyway:
                // without an explicit authorizeHttpRequests rule, Spring Security never installs an
                // authorization filter for this chain, so the default AnonymousAuthenticationFilter's
                // token (isAuthenticated() == true) satisfies the authorization server's own
                // authentication check and it would issue authorization codes for "anonymousUser".
                .authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated())
                .requestCache((cache) -> cache.requestCache(new HttpSessionRequestCache()))
                .exceptionHandling((ex) -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @ConditionalOnWebApplication(type = Type.SERVLET)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, OidcUserService oidcUserService,
                                                          RevokeAuthorizedClientLogoutHandler revokeAuthorizedClientLogoutHandler,
                                                          @Value("${openapi.libreHousehold.base-path:/v1}") String basePath)
            throws Exception {
        http.authorizeHttpRequests((authorize) -> authorize
                        // All generated API controllers are mounted under this base path (see the
                        // @RequestMapping on the generated *ApiController classes) — read from the
                        // same property so the two locations cannot drift apart. "/login" and "/error"
                        // are Spring's own endpoints, never under the API base path. "/error" must stay
                        // public too: Spring MVC forwards internally to it for any non-2xx response
                        // (e.g. validation failures, 404s) even from an otherwise permitAll endpoint,
                        // and that internal forward is itself a new request subject to these very rules.
                        .requestMatchers("/login", "/error",
                                basePath + "/household/setup",
                                basePath + "/invite/**",
                                basePath + "/members/availability")
                        .permitAll()
                        .anyRequest().authenticated())
                .cors(Customizer.withDefaults())
                // Both filter chains share the same HttpSession, so without this, an unauthenticated
                // 401 on a business endpoint (e.g. the frontend's own bootstrapSession() GET /me,
                // fired on every page load including the login page itself) would overwrite the
                // /oauth2/authorize continuation already saved by authorizationServerSecurityFilterChain,
                // causing login to redirect back to /me instead of resuming the original request.
                // A plain NullRequestCache would also break resuming that continuation: formLogin()'s
                // SavedRequestAwareAuthenticationSuccessHandler below shares this very RequestCache
                // instance and needs to still be able to *read* it (see NonSavingRequestCache).
                .requestCache((cache) -> cache.requestCache(new NonSavingRequestCache()))
                // ADR-014: XSRF-TOKEN must be JS-readable (spa() wires a cookie-based repository plus
                // a request handler that resolves the raw, unmasked token value, matching what the SPA
                // reads straight from the cookie). No permitAll exception from CSRF is added here for
                // /household/setup or /invite/{token}/join even though they are unauthenticated: the
                // SPA already performs a preceding GET (e.g. /members/availability, /invite/{token})
                // that hands it a fresh XSRF-TOKEN cookie before the POST (see P1.5.2).
                .csrf(CsrfConfigurer::spa)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .formLogin(Customizer.withDefaults())
                .oauth2Login((login) -> login.userInfoEndpoint((userInfo) ->
                        userInfo.oidcUserService(oidcUserService)))
                // ADR-014: logout must actively revoke the backend's cached tokens for this user,
                // not just invalidate the session (see RevokeAuthorizedClientLogoutHandler).
                // POST /logout is therefore fully handled here, not by SessionApiDelegateImpl.
                .logout((logout) -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler(revokeAuthorizedClientLogoutHandler)
                        .logoutSuccessHandler((_, response, _) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value())))
                // Browser navigations (Accept: text/html) get redirected to the login page;
                // API/XHR calls from the SPA get a plain 401 instead of an HTML redirect.
                .exceptionHandling((ex) -> ex
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML))
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                AnyRequestMatcher.INSTANCE));
        return http.build();
    }
}
