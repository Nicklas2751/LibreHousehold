package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.config.GitHubEmailClient.GitHubEmail;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GitHubEmailAwareOAuth2UserServiceTest {

    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    @Mock
    private GitHubEmailClient gitHubEmailClient;

    @Nested
    class loadUser {

        @Test
        void githubUserWithVerifiedPrimaryEmail_addsVerifiedEmailToAttributes() {
            // given
            var userRequest = userRequest(CommonOAuth2Provider.GITHUB.getBuilder("github"));
            var githubUser = new DefaultOAuth2User(List.of(),
                    Map.of("id", 4711, "login", "octocat", "email", "public@example.com"), "id");
            doReturn(githubUser).when(delegate).loadUser(userRequest);
            doReturn(List.of(
                    new GitHubEmail("secondary@example.com", false, true),
                    new GitHubEmail("primary@example.com", true, true)))
                    .when(gitHubEmailClient).fetchEmails(ACCESS_TOKEN);
            var expected = new DefaultOAuth2User(List.of(),
                    Map.of("id", 4711, "login", "octocat", "email", "primary@example.com", "email_verified", true), "id");

            // when
            var result = userService().loadUser(userRequest);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        static List<Arguments> emailsWithoutVerifiedPrimary() {
            return List.of(
                    argumentSet("unverified primary e-mail", List.of(new GitHubEmail("primary@example.com", true, false))),
                    argumentSet("verified e-mail that is not primary", List.of(new GitHubEmail("other@example.com", false, true))),
                    argumentSet("no e-mail at all", List.of()));
        }

        @ParameterizedTest
        @MethodSource("emailsWithoutVerifiedPrimary")
        void githubUserWithoutVerifiedPrimaryEmail_returnsUserUnchanged(List<GitHubEmail> emails) {
            // given
            var userRequest = userRequest(CommonOAuth2Provider.GITHUB.getBuilder("github"));
            var githubUser = new DefaultOAuth2User(List.of(),
                    Map.of("id", 4711, "login", "octocat", "email", "public@example.com"), "id");
            doReturn(githubUser).when(delegate).loadUser(userRequest);
            doReturn(emails).when(gitHubEmailClient).fetchEmails(ACCESS_TOKEN);

            // when
            var result = userService().loadUser(userRequest);

            // then
            assertThat(result).isSameAs(githubUser);
        }

        /**
         * Configured scopes replace the defaults, so the token may lack user:email: logging in
         * needs no e-mail and must keep working.
         */
        @Test
        void githubEmailsNotAccessible_returnsUserUnchanged() {
            // given
            var userRequest = userRequest(CommonOAuth2Provider.GITHUB.getBuilder("github"));
            var githubUser = new DefaultOAuth2User(List.of(), Map.of("id", 4711, "login", "octocat"), "id");
            doReturn(githubUser).when(delegate).loadUser(userRequest);
            doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null))
                    .when(gitHubEmailClient).fetchEmails(ACCESS_TOKEN);

            // when
            var result = userService().loadUser(userRequest);

            // then
            assertThat(result).isSameAs(githubUser);
        }

        @Test
        void otherProvider_returnsUserUnchanged() {
            // given
            var userRequest = userRequest(ClientRegistration.withRegistrationId("forgejo")
                    .clientId("client-id")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}")
                    .authorizationUri("https://git.example.com/login/oauth/authorize")
                    .tokenUri("https://git.example.com/login/oauth/access_token")
                    .userInfoUri("https://git.example.com/api/v1/user")
                    .userNameAttributeName("login"));
            var forgejoUser = new DefaultOAuth2User(List.of(), Map.of("login", "octocat"), "login");
            doReturn(forgejoUser).when(delegate).loadUser(userRequest);

            // when
            var result = userService().loadUser(userRequest);

            // then
            assertThat(result).isSameAs(forgejoUser);
        }

        @Test
        void otherProvider_fetchesNoGitHubEmails() {
            // given
            var userRequest = userRequest(CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId("client-id"));
            doReturn(new DefaultOAuth2User(List.of(), Map.of("sub", "subject"), "sub")).when(delegate).loadUser(userRequest);

            // when
            userService().loadUser(userRequest);

            // then
            verifyNoInteractions(gitHubEmailClient);
        }
    }

    private GitHubEmailAwareOAuth2UserService userService() {
        return new GitHubEmailAwareOAuth2UserService(delegate, gitHubEmailClient, new CommonProvider());
    }

    private OAuth2UserRequest userRequest(ClientRegistration.Builder registration) {
        var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, ACCESS_TOKEN,
                Instant.now(), Instant.now().plusSeconds(300));
        return new OAuth2UserRequest(registration.clientId("client-id").build(), accessToken);
    }
}
