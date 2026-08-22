package eu.wiegandt.librehousehold.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Idempotently seeds the single, statically known {@code RegisteredClient} for the SPA-backend
 * BFF (see DD-9). Redirect URI and client secret are configurable so self-hosters only need to
 * set properties, not code. The client is confidential ({@link ClientAuthenticationMethod#CLIENT_SECRET_BASIC})
 * because Spring Authorization Server does not issue refresh tokens to public clients (see DD-7
 * correction).
 */
@Component
public class RegisteredClientSeeder implements ApplicationRunner {

    public static final String CLIENT_ID = "spa-backend-client";
    private static final Duration ACCESS_TOKEN_TIME_TO_LIVE = Duration.ofMinutes(10);
    private static final Duration REFRESH_TOKEN_TIME_TO_LIVE = Duration.ofDays(30);

    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final String redirectUri;
    private final String clientSecret;

    public RegisteredClientSeeder(RegisteredClientRepository registeredClientRepository,
                                  PasswordEncoder passwordEncoder,
                                  @Value("${librehousehold.security.oauth2-client.redirect-uri}") String redirectUri,
                                  @Value("${librehousehold.security.oauth2-client.client-secret}") String clientSecret) {
        this.registeredClientRepository = registeredClientRepository;
        this.passwordEncoder = passwordEncoder;
        this.redirectUri = redirectUri;
        this.clientSecret = clientSecret;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(redirectUri);
    }

    public void seed(String redirectUri) {
        if (registeredClientRepository.findByClientId(CLIENT_ID) != null) {
            return;
        }
        registeredClientRepository.save(buildRegisteredClient(redirectUri));
    }

    private RegisteredClient buildRegisteredClient(String redirectUri) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(CLIENT_ID)
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri)
                .scope(OidcScopes.OPENID)
                .clientSettings(ClientSettings.builder().requireProofKey(true).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(ACCESS_TOKEN_TIME_TO_LIVE)
                        .refreshTokenTimeToLive(REFRESH_TOKEN_TIME_TO_LIVE)
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }
}
