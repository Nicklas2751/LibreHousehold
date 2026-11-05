package eu.wiegandt.librehousehold.config;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * Recognizes a GitHub registration by its user-info endpoint, independent of the registration ID a
 * self-hoster chose. GitHub is no OIDC provider and needs special handling for the e-mail address
 * (see {@link GitHubEmailAwareOAuth2UserService}, {@link SocialLoginRegistrationCheck}).
 */
final class GitHubProvider {

    private static final String API_HOST = "api.github.com";

    private GitHubProvider() {
    }

    static boolean isGitHub(ClientRegistration registration) {
        var userInfoUri = registration.getProviderDetails().getUserInfoEndpoint().getUri();
        return StringUtils.hasText(userInfoUri) && API_HOST.equals(URI.create(userInfoUri).getHost());
    }
}
