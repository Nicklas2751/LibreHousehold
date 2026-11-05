package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class AccountPrincipalAuthorizationManagerTest {

    @Nested
    class authorize {

        static List<Arguments> authentications() {
            var externalUser = new DefaultOAuth2User(List.of(), Map.of("sub", "external-subject"), "sub");
            return List.of(
                    argumentSet("account principal",
                            UsernamePasswordAuthenticationToken.authenticated(
                                    AccountPrincipal.identifiedBy(UUID.randomUUID()), null, List.of()), true),
                    argumentSet("OAuth2 authentication token",
                            new OAuth2AuthenticationToken(externalUser, List.of(), "google"), false),
                    argumentSet("anonymous",
                            new AnonymousAuthenticationToken("key", "anonymousUser",
                                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")), false),
                    argumentSet("no authentication", null, false));
        }

        @ParameterizedTest
        @MethodSource("authentications")
        void anyAuthentication_grantedOnlyForAccountPrincipal(Authentication authentication, boolean expectedGranted) {
            // given
            var context = new RequestAuthorizationContext(new MockHttpServletRequest());

            // when
            var result = new AccountPrincipalAuthorizationManager().authorize(() -> authentication, context);

            // then
            assertThat(result.isGranted()).isEqualTo(expectedGranted);
        }
    }
}
