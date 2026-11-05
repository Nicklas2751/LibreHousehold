package eu.wiegandt.librehousehold.config;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Fails the startup for social-login registrations that would only break at the first login (see
 * Arc42 Chapter 7, social-login provider requirements). Checks the effective registrations Spring
 * Boot built from {@code spring.security.oauth2.client.*}, i.e. after its provider defaults
 * (e.g. {@code openid, profile, email} for Google, only {@code read:user} for GitHub) and issuer
 * discovery, which sets no scopes.
 * <ul>
 *     <li>An issuer requires the {@code openid} scope and vice versa: only then are {@code nonce}
 *     and the ID token validated against the issuer.</li>
 *     <li>Household setup and invite join need the verified e-mail address, so the {@code email}
 *     scope is required; GitHub has no such scope and needs {@code user:email} instead.</li>
 * </ul>
 */
@Component
class SocialLoginRegistrationCheck {

    private static final String EMAIL_SCOPE = "email";
    private static final String GITHUB_EMAIL_SCOPE = "user:email";

    void check(Iterable<ClientRegistration> registrations) {
        registrations.forEach(SocialLoginRegistrationCheck::check);
    }

    private static void check(ClientRegistration registration) {
        var scopes = registration.getScopes();
        var hasIssuer = StringUtils.hasText(registration.getProviderDetails().getIssuerUri());
        var hasOpenidScope = scopes.contains(OidcScopes.OPENID);
        if (hasIssuer && !hasOpenidScope) {
            throw invalid(registration, "has an issuer-uri but lacks the 'openid' scope");
        }
        if (hasOpenidScope && !hasIssuer) {
            throw invalid(registration, "requests the 'openid' scope but has no issuer-uri");
        }
        var requiredEmailScope = GitHubProvider.isGitHub(registration) ? GITHUB_EMAIL_SCOPE : EMAIL_SCOPE;
        if (!scopes.contains(requiredEmailScope)) {
            throw invalid(registration, "lacks the '" + requiredEmailScope + "' scope");
        }
    }

    private static IllegalStateException invalid(ClientRegistration registration, String reason) {
        return new IllegalStateException("Social login registration '" + registration.getRegistrationId() + "' "
                + reason + ". Configured scopes: " + Set.copyOf(registration.getScopes()));
    }
}
