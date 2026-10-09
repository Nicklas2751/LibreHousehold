package eu.wiegandt.librehousehold.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

/**
 * Reads the {@code librehousehold.security.social-login.*} namespace (see ADR-016): a
 * per-provider map of external OIDC/OAuth2 login providers, turned into
 * {@link org.springframework.security.oauth2.client.registration.ClientRegistration}s by
 * {@link SocialLoginClientRegistrationFactory}. {@code providers} binds to an empty map via
 * {@link DefaultValue} when absent, so callers never need a null check when {@code enabled} is
 * {@code false} or no providers are configured.
 */
@ConfigurationProperties(prefix = "librehousehold.security.social-login")
public record SocialLoginProperties(boolean enabled, @DefaultValue Map<String, ProviderConfig> providers) {

    public enum ProviderType {
        OIDC, OAUTH2
    }

    /**
     * Discriminated by {@link #type()}. {@code OIDC} providers either set {@code issuerUri}
     * (generic self-hosted OIDC, resolved via issuer discovery) or leave it unset for a provider
     * name Spring Security already knows ({@code CommonOAuth2Provider}, e.g. {@code google}).
     * {@code OAUTH2} providers that are not well-known instead set the explicit endpoint fields.
     * A future provider type can be added as a new {@link ProviderType} constant plus additional
     * optional fields here without breaking existing bindings.
     */
    public record ProviderConfig(
            ProviderType type,
            String issuerUri,
            String clientId,
            String clientSecret,
            String displayName,
            String authorizationUri,
            String tokenUri,
            String userInfoUri,
            String userNameAttribute) {
    }
}
