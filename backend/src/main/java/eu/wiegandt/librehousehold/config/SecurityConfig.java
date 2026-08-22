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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

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
 */
@Configuration
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
     */
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${librehousehold.security.oauth2-authorization-server.issuer}") String issuer,
            @Value("${librehousehold.security.oauth2-client.redirect-uri}") String redirectUri,
            @Value("${librehousehold.security.oauth2-client.client-secret}") String clientSecret) {
        var clientRegistration = ClientRegistration.withRegistrationId(RegisteredClientSeeder.CLIENT_ID)
                .clientId(RegisteredClientSeeder.CLIENT_ID)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope(OidcScopes.OPENID)
                .authorizationUri(issuer + "/oauth2/authorize")
                .tokenUri(issuer + "/oauth2/token")
                .jwkSetUri(issuer + "/oauth2/jwks")
                .userInfoUri(issuer + "/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName(RegisteredClientSeeder.CLIENT_ID)
                .build();
        return new InMemoryClientRegistrationRepository(clientRegistration);
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
