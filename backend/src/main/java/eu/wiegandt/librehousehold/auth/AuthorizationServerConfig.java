package eu.wiegandt.librehousehold.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher;
import jakarta.servlet.DispatcherType;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Security configuration for the OAuth2 Authorization Server.
 * <p>
 * {@code @EnableWebSecurity} is technically redundant with Spring Boot because
 * {@code SecurityAutoConfiguration} already activates web security via auto-configuration.
 * It is kept here to make the intent explicit: this class owns the security setup and
 * intentionally replaces Spring Boot's defaults.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({AuthProperties.class, AuthProviderProperties.class})
class AuthorizationServerConfig {

    /**
     * Security filter chain for the Authorization Server's own endpoints (evaluated first, order 1).
     * <p>
     * {@code http.oauth2AuthorizationServer(...)} is the Spring Security 7 DSL that internally
     * creates an {@code OAuth2AuthorizationServerConfigurer} and applies it to the HTTP security.
     * The configurer's endpoint matcher restricts this chain to OAuth2/OIDC paths only:
     * {@code /oauth2/authorize}, {@code /oauth2/token}, {@code /oauth2/jwks},
     * {@code /.well-known/openid-configuration}, and a few others.
     * All other paths fall through to the default chain (order 2).
     * <p>
     * When an unauthenticated browser requests {@code /oauth2/authorize}, the exception handler
     * redirects it to {@code /login} (the form-login page configured in the default chain)
     * instead of returning a 401. This is the standard OAuth2 authorization code flow entry point.
     */
    @Bean
    @Order(1)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity httpSecurity) {
        httpSecurity
                .oauth2AuthorizationServer(authorizationServerConfigurer -> {
                    httpSecurity.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher());
                    authorizationServerConfigurer.oidc(Customizer.withDefaults());
                })
                .authorizeHttpRequests(registry -> registry.anyRequest().authenticated())
                .exceptionHandling(exceptionHandlingConfigurer -> exceptionHandlingConfigurer
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return httpSecurity.build();
    }

    /**
     * Default security filter chain for all non-Authorization-Server endpoints (order 2).
     * <p>
     * This chain secures the REST API ({@code /v1/**}) and the login page.
     * <p>
     * Form login is required because the authorization code flow (chain 1) redirects
     * unauthenticated users to {@code /login}, which is served by Thymeleaf and handled here.
     * <p>
     * OAuth2 login enables federated identity (e.g. Google). The {@link FederatedIdentityOidcUserService}
     * handles JIT provisioning. On {@link OAuth2AuthenticationException} the failure handler encodes
     * the error code into a redirect so Thymeleaf can display a localised message.
     * <p>
     * The JWT resource server configuration enables Bearer token authentication for REST API
     * calls. It uses the same {@code JWKSource} as the authorization server (auto-configured
     * by Spring Boot), so tokens issued here can also be verified here.
     * <p>
     * Spring Boot's default {@code SecurityFilterChain} auto-configuration backs off entirely
     * as soon as any custom {@code SecurityFilterChain} bean is present, so this bean must
     * define all required rules explicitly.
     */
    @Bean
    @Order(2)
    @SuppressWarnings("java:S4502")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity httpSecurity,
                                                   FederatedIdentityOidcUserService federatedIdentityOidcUserService,
                                                   ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        httpSecurity
                .csrf(csrf -> csrf
                        .spa()
                        // It's safe to disable CSRF for the API since we use header based JWT Authentication
                        .ignoringRequestMatchers("/v1/**"))
                .authorizeHttpRequests(registry -> registry
                        // ERROR-dispatched requests must stay permitAll: an ErrorResponseException (e.g.
                        // InvalidInviteException) resolved on a permitAll endpoint triggers an internal
                        // forward to the error dispatcher, which would otherwise be blocked by
                        // anyRequest().authenticated() and mask the real status code with a 401/302.
                        .requestMatchers(new DispatcherTypeRequestMatcher(DispatcherType.ERROR)).permitAll()
                        .requestMatchers("/v1/auth/providers", "/v1/auth/register", "/v1/invite/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .failureHandler(createLoginFormFailureHandler()))
                .oauth2ResourceServer(resourceServerConfigurer -> resourceServerConfigurer.jwt(Customizer.withDefaults()));

        // oauth2Login is only activated when at least one provider (e.g. Google) is configured
        if (clientRegistrations.getIfAvailable() != null) {
            httpSecurity.oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(uie -> uie.oidcUserService(federatedIdentityOidcUserService))
                    .failureHandler(createOauth2FailureHandler()));
        }

        return httpSecurity.build();
    }

    private @NonNull AuthenticationFailureHandler createOauth2FailureHandler() {
        return (_, response, exception) -> {
            var errorCode = (exception instanceof OAuth2AuthenticationException oae)
                    ? oae.getError().getErrorCode()
                    : "authentication_error";
            redirectToLoginWithError(response, errorCode);
        };
    }

    private void redirectToLoginWithError(HttpServletResponse response, String errorCode) throws IOException {
        response.sendRedirect("/login?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8));
    }

    private @NonNull AuthenticationFailureHandler createLoginFormFailureHandler() {
        return (_, response, exception) -> {
            var code = switch (exception) {
                case BadCredentialsException _ -> "bad_credentials";
                case LockedException _ -> "account_locked";
                default -> "authentication_error";
            };
            redirectToLoginWithError(response, code);
        };
    }

    /**
     * Defines the single registered OAuth2 client: the LibreHousehold SPA.
     * <p>
     * The SPA uses the Authorization Code flow with PKCE and no client secret
     * ({@link ClientAuthenticationMethod#NONE}). PKCE is mandatory because browser
     * applications cannot store a secret securely. Refresh tokens allow the SPA to
     * obtain new access tokens without re-prompting the user, and are rotated on
     * every use ({@code reuseRefreshTokens = false}) to limit the impact of token theft.
     */
    @Bean
    RegisteredClientRepository registeredClientRepository(AuthProperties authProperties) {
        var singlePageApplicationClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(authProperties.clientId())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(authProperties.redirectUri())
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(authProperties.accessTokenTimeToLive())
                        .refreshTokenTimeToLive(authProperties.refreshTokenTimeToLive())
                        .reuseRefreshTokens(false)
                        .build())
                .build();
        return new InMemoryRegisteredClientRepository(singlePageApplicationClient);
    }

    /**
     * Password encoder using Argon2id as mandated by ADR-009.
     * Argon2id is the winner of the Password Hashing Competition and provides better
     * resistance against GPU/ASIC attacks than BCrypt. BouncyCastle (bcpkix-jdk18on)
     * is required on the classpath for this encoder to function.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * Persists OAuth2 authorization state (authorization codes, access tokens, refresh tokens)
     * in the database via JDBC.
     * <p>
     * Without this bean, Spring would fall back to an in-memory store that loses all active
     * sessions on application restart. The required schema ({@code oauth2_authorization} table
     * and siblings) is created by the root Flyway migration {@code V1__create_event_publication.sql}.
     */
    @Bean
    OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                    RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }
}