package eu.wiegandt.librehousehold.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import eu.wiegandt.librehousehold.core.ResourceOwnerQuery;
import java.util.UUID;

import org.springframework.security.authorization.AuthorizationDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class AuthorCheckerTest {

    @Mock
    ResourceOwnerQuery resourceOwnerQuery;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class isAuthor {

        @Test
        void callerOwnsResource_returnsTrue() {
            // given
            var accountId = UUID.randomUUID();
            var resourceId = UUID.randomUUID();
            setUpJwt(accountId);
            var checker = new AuthorChecker(List.of(resourceOwnerQuery));
            doReturn(true).when(resourceOwnerQuery).isOwner(resourceId, accountId);

            // when
            var result = checker.isAuthor(resourceId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void callerDoesNotOwnResource_returnsFalse() {
            // given
            var accountId = UUID.randomUUID();
            var resourceId = UUID.randomUUID();
            setUpJwt(accountId);
            var checker = new AuthorChecker(List.of(resourceOwnerQuery));
            doReturn(false).when(resourceOwnerQuery).isOwner(resourceId, accountId);

            // when
            var result = checker.isAuthor(resourceId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void tokenWithoutSubject_throwsAuthorizationDeniedException() {
            // given
            var resourceId = UUID.randomUUID();
            var jwt = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .claim("household_id", UUID.randomUUID().toString())
                    .build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
            var checker = new AuthorChecker(List.of(resourceOwnerQuery));

            // when / then
            assertThatThrownBy(() -> checker.isAuthor(resourceId))
                    .isInstanceOf(AuthorizationDeniedException.class);
        }
    }

    private void setUpJwt(UUID accountId) {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(accountId.toString())
                .claim("household_id", UUID.randomUUID().toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
