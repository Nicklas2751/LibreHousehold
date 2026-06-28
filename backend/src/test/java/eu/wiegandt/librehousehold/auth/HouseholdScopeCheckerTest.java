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

class HouseholdScopeCheckerTest {

    private final HouseholdScopeChecker checker = new HouseholdScopeChecker();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class isCurrentUserInHousehold {

        @Test
        void matchingHouseholdId_returnsTrue() {
            // given
            var householdId = UUID.randomUUID();
            setUpJwtWithHouseholdId(householdId);

            // when
            var result = checker.isCurrentUserInHousehold(householdId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void differentHouseholdId_returnsFalse() {
            // given
            var householdId = UUID.randomUUID();
            setUpJwtWithHouseholdId(UUID.randomUUID());

            // when
            var result = checker.isCurrentUserInHousehold(householdId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void nullHouseholdIdInJwt_throwsAuthorizationDeniedException() {
            // given
            var jwt = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .subject(UUID.randomUUID().toString())
                    .build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

            // when / then
            assertThatThrownBy(() -> checker.isCurrentUserInHousehold(UUID.randomUUID()))
                    .isInstanceOf(AuthorizationDeniedException.class);
        }
    }

    private void setUpJwtWithHouseholdId(UUID householdId) {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("household_id", householdId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
