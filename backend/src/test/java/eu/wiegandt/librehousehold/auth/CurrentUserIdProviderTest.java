package eu.wiegandt.librehousehold.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserIdProviderTest {

    private final CurrentUserIdProvider provider = new CurrentUserIdProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class getCurrentUserId {

        @Test
        void validJwtWithSubject_returnsSubjectAsUuid() {
            // given
            var userId = UUID.randomUUID();
            var jwt = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .subject(userId.toString())
                    .build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

            // when
            var result = provider.getCurrentUserId();

            // then
            assertThat(result).isEqualTo(userId);
        }

        @Test
        void noJwtAuthentication_throwsAuthorizationDeniedException() {
            // given — no authentication set

            // when / then
            assertThatThrownBy(provider::getCurrentUserId)
                    .isInstanceOf(AuthorizationDeniedException.class);
        }
    }
}
