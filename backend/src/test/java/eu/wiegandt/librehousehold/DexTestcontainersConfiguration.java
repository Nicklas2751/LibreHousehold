package eu.wiegandt.librehousehold;

import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.DynamicPropertyRegistry;
import wf.garnier.testcontainers.dexidp.DexContainer;

/**
 * Starts Dex as a local OIDC provider and registers it as the social-login provider
 * {@value #REGISTRATION_ID}, so the social-login flow can be exercised without real third-party
 * credentials. Kept separate from {@link TestcontainersConfiguration} because importing it enables
 * social login for the whole application context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DexTestcontainersConfiguration {

    private static final String REGISTRATION_ID = "dev-provider";
    public static final String CLIENT_ID = "librehousehold-dev";
    private static final String CLIENT_SECRET = "librehousehold-dev-secret";
    private static final String USER_NAME = "dev";
    private static final String USER_EMAIL = "dev@example.com";
    private static final String USER_PASSWORD = "password";

    private static final String DISPLAY_NAME = "Dex (local dev)";
    // DexContainer.Client accepts exactly one redirect URI. It's the Vite dev proxy origin, because
    // the proxy forwards /oauth2 with X-Forwarded-Host, so the backend builds its redirect URI from
    // that origin when a developer logs in through the frontend.
    private static final String REDIRECT_URI = "http://localhost:5173/login/oauth2/code/" + REGISTRATION_ID;
    private static final String PROVIDER_PREFIX = "librehousehold.security.social-login.providers." + REGISTRATION_ID;

    @Bean
    DexContainer dexContainer() {
        return new DexContainer(DexContainer.DEFAULT_IMAGE_NAME.withTag(DexContainer.DEFAULT_TAG))
                .withClient(new DexContainer.Client(CLIENT_ID, CLIENT_SECRET, REDIRECT_URI))
                .withUser(new DexContainer.User(USER_NAME, USER_EMAIL, USER_PASSWORD));
    }

    // Injecting the container makes Spring start it before the issuer-uri supplier is first resolved,
    // which happens when SecurityConfig#clientRegistrationRepository binds SocialLoginProperties.
    @Bean
    DynamicPropertyRegistrar dexPropertyRegistrar(DexContainer dexContainer) {
        return registry -> registerDevProvider(registry, dexContainer);
    }

    private static void registerDevProvider(DynamicPropertyRegistry registry, DexContainer dexContainer) {
        registry.add("librehousehold.security.social-login.enabled", () -> true);
        registry.add(PROVIDER_PREFIX + ".type", () -> ProviderType.OIDC);
        registry.add(PROVIDER_PREFIX + ".issuer-uri", dexContainer::getIssuerUri);
        registry.add(PROVIDER_PREFIX + ".client-id", () -> CLIENT_ID);
        registry.add(PROVIDER_PREFIX + ".client-secret", () -> CLIENT_SECRET);
        registry.add(PROVIDER_PREFIX + ".display-name", () -> DISPLAY_NAME);
    }
}
