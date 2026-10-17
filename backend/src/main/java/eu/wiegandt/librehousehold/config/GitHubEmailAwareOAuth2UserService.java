package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.config.GitHubEmailClient.GitHubEmail;
import org.jspecify.annotations.NonNull;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * User service for social-login providers without OIDC. For GitHub, it replaces the public
 * e-mail address from the user endpoint with the verified primary address from
 * {@code /user/emails} and marks it with the OIDC claim {@code email_verified}. Registering via
 * social login thereby reads the verified e-mail address the same way for every provider.
 * Without a verified primary address, the user stays unchanged and therefore has no
 * {@code email_verified} attribute.
 */
class GitHubEmailAwareOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final GitHubEmailClient gitHubEmailClient;
    private final CommonProvider commonProvider;

    GitHubEmailAwareOAuth2UserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate,
                                      GitHubEmailClient gitHubEmailClient, CommonProvider commonProvider) {
        this.delegate = delegate;
        this.gitHubEmailClient = gitHubEmailClient;
        this.commonProvider = commonProvider;
    }

    @Override
    public OAuth2User loadUser(@NonNull OAuth2UserRequest userRequest) {
        var user = delegate.loadUser(userRequest);
        var registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (commonProvider.find(registrationId).filter(CommonOAuth2Provider.GITHUB::equals).isEmpty()) {
            return user;
        }
        return findVerifiedPrimaryEmail(userRequest)
                .map(email -> withVerifiedEmail(user, email, userRequest))
                .orElse(user);
    }

    // Configured scopes replace the defaults, so the token may lack user:email. A plain login
    // needs no e-mail address and must not fail because of that.
    private Optional<String> findVerifiedPrimaryEmail(OAuth2UserRequest userRequest) {
        List<GitHubEmail> emails;
        try {
            emails = gitHubEmailClient.fetchEmails(userRequest.getAccessToken().getTokenValue());
        } catch (HttpClientErrorException _) {
            return Optional.empty();
        }
        return emails.stream()
                .filter(GitHubEmail::primary)
                .filter(GitHubEmail::verified)
                .map(GitHubEmail::email)
                .findFirst();
    }

    private static OAuth2User withVerifiedEmail(OAuth2User user, String email, OAuth2UserRequest userRequest) {
        var attributes = new HashMap<>(user.getAttributes());
        attributes.put(StandardClaimNames.EMAIL, email);
        attributes.put(StandardClaimNames.EMAIL_VERIFIED, true);
        var userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();
        return new DefaultOAuth2User(user.getAuthorities(), attributes, userNameAttributeName);
    }
}
