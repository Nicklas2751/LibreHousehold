package eu.wiegandt.librehousehold.config;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Identifies the social-login provider of a federated identity (see ADR-018). Together with the
 * provider's subject, it is the key an account is linked by.
 *
 * <p>The registration ID is no such key: a self-hoster may rename it, and two registrations may
 * point to different providers that issue the same subject. Instead, an OIDC provider is identified
 * by its issuer, which Spring Security validates against the ID token's {@code iss} claim
 * ({@link SocialLoginRegistrationCheck} guarantees every OIDC registration has one). A plain OAuth2
 * provider has no issuer, so its user-info endpoint stands in; query and fragment are dropped,
 * since they only select what the endpoint returns, while the path may tell providers on the same
 * origin apart.
 */
final class FederatedProviderKey {

    private FederatedProviderKey() {
    }

    static String of(ClientRegistration registration) {
        var providerDetails = registration.getProviderDetails();
        var issuer = providerDetails.getIssuerUri();
        if (StringUtils.hasText(issuer)) {
            return issuer;
        }
        return UriComponentsBuilder.fromUriString(providerDetails.getUserInfoEndpoint().getUri())
                .replaceQuery(null)
                .fragment(null)
                .toUriString();
    }
}
