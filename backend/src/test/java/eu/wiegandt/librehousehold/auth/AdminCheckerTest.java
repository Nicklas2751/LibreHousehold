package eu.wiegandt.librehousehold.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminCheckerTest {

    private final AdminChecker checker = new AdminChecker();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class isAdmin {

        @Test
        void adminRole_returnsTrue() {
            // given
            setUpJwtWithRole("admin", UUID.randomUUID());

            // when
            var result = checker.isAdmin();

            // then
            assertThat(result).isTrue();
        }

        @Test
        void memberRole_returnsFalse() {
            // given
            setUpJwtWithRole("member", UUID.randomUUID());

            // when
            var result = checker.isAdmin();

            // then
            assertThat(result).isFalse();
        }
    }

    private void setUpJwtWithRole(String role, UUID householdId) {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("role", role)
                .claim("household_id", householdId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
