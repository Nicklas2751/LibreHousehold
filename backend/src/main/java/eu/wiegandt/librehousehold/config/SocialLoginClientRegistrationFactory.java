package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderConfig;
import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderType;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Builds one {@link ClientRegistration} per enabled external social-login provider configured
 * under {@code librehousehold.security.social-login.providers} (see ADR-016).
 * Resolution order per provider, mirroring Spring Security's own recommended precedence: explicit
 * non-blank {@code issuer-uri} on a {@code type: oidc} provider (OIDC discovery) first, then a
 * well-known {@link CommonOAuth2Provider} name match (e.g. {@code google}, {@code github}),
 * finally a fully explicit, manually built registration for self-hosted OAuth2 providers Spring
 * Security does not know out of the box.
 */
@Component
public class SocialLoginClientRegistrationFactory {

    private static final String DEFAULT_REDIRECT_URI = "{baseUrl}/{action}/oauth2/code/{registrationId}";

    private final CommonProvider commonProvider;
    private final OidcIssuerDiscovery oidcIssuerDiscovery;

    public SocialLoginClientRegistrationFactory(CommonProvider commonProvider, OidcIssuerDiscovery oidcIssuerDiscovery) {
        this.commonProvider = commonProvider;
        this.oidcIssuerDiscovery = oidcIssuerDiscovery;
    }

    public List<ClientRegistration> buildClientRegistrations(SocialLoginProperties properties) {
        if (!properties.enabled()) {
            return List.of();
        }
        return properties.providers().entrySet().stream()
                .map(entry -> buildClientRegistration(entry.getKey(), entry.getValue()))
                .toList();
    }

    private ClientRegistration buildClientRegistration(String registrationId, ProviderConfig config) {
        if (config.type() == ProviderType.OIDC && StringUtils.hasText(config.issuerUri())) {
            return buildFromIssuerDiscovery(registrationId, config);
        }
        return commonProvider.find(registrationId)
                .map(provider -> buildFromCommonProvider(provider, registrationId, config))
                .orElseGet(() -> buildExplicit(registrationId, config));
    }

    private ClientRegistration buildFromIssuerDiscovery(String registrationId, ProviderConfig config) {
        return oidcIssuerDiscovery.discover(config.issuerUri())
                .registrationId(registrationId)
                .clientId(config.clientId())
                .clientSecret(config.clientSecret())
                .clientName(config.displayName())
                .build();
    }

    private ClientRegistration buildFromCommonProvider(CommonOAuth2Provider provider, String registrationId,
                                                       ProviderConfig config) {
        return provider.getBuilder(registrationId)
                .clientId(config.clientId())
                .clientSecret(config.clientSecret())
                .clientName(config.displayName())
                .build();
    }

    private ClientRegistration buildExplicit(String registrationId, ProviderConfig config) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId(config.clientId())
                .clientSecret(config.clientSecret())
                .clientName(config.displayName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(DEFAULT_REDIRECT_URI)
                .authorizationUri(config.authorizationUri())
                .tokenUri(config.tokenUri())
                .userInfoUri(config.userInfoUri())
                .userNameAttributeName(config.userNameAttribute())
                .build();
    }
}
