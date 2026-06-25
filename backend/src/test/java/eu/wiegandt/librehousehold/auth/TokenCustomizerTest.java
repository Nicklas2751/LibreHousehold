package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.household.MemberQuery;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenCustomizerTest {

    @InjectMocks
    TokenCustomizer tokenCustomizer;

    @Mock
    MemberQuery memberQuery;

    @Mock
    JwtEncodingContext context;

    @Mock
    Authentication authentication;

    @Nested
    class customize {

        @Test
        void accessToken_addsSubClaim() {
            // given
            var memberId = UUID.randomUUID();
            var claimsBuilder = JwtClaimsSet.builder();
            setupAccessTokenContext(memberId, claimsBuilder, Optional.empty(), false);

            // when
            tokenCustomizer.customize(context);

            // then
            assertThat(claimsBuilder.build().<String>getClaim("sub")).isEqualTo(memberId.toString());
        }

        @Test
        void accessToken_withHousehold_addsHouseholdIdClaim() {
            // given
            var memberId = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var claimsBuilder = JwtClaimsSet.builder();
            setupAccessTokenContext(memberId, claimsBuilder, Optional.of(householdId), false);

            // when
            tokenCustomizer.customize(context);

            // then
            assertThat(claimsBuilder.build().<String>getClaim("household_id")).isEqualTo(householdId.toString());
        }

        @Test
        void accessToken_withoutHousehold_setsHouseholdIdNull() {
            // given
            var memberId = UUID.randomUUID();
            var claimsBuilder = JwtClaimsSet.builder();
            setupAccessTokenContext(memberId, claimsBuilder, Optional.empty(), false);

            // when
            tokenCustomizer.customize(context);

            // then
            assertThat(claimsBuilder.build().<String>getClaim("household_id")).isNull();
        }

        @Test
        void accessToken_asAdmin_addsAdminRoleClaim() {
            // given
            var memberId = UUID.randomUUID();
            var claimsBuilder = JwtClaimsSet.builder();
            setupAccessTokenContext(memberId, claimsBuilder, Optional.empty(), true);

            // when
            tokenCustomizer.customize(context);

            // then
            assertThat(claimsBuilder.build().<String>getClaim("role")).isEqualTo("admin");
        }

        @Test
        void accessToken_asMember_addsMemberRoleClaim() {
            // given
            var memberId = UUID.randomUUID();
            var claimsBuilder = JwtClaimsSet.builder();
            setupAccessTokenContext(memberId, claimsBuilder, Optional.empty(), false);

            // when
            tokenCustomizer.customize(context);

            // then
            assertThat(claimsBuilder.build().<String>getClaim("role")).isEqualTo("member");
        }

        @Test
        void accessToken_addsLocalProviderClaim() {
            // given
            var memberId = UUID.randomUUID();
            var claimsBuilder = JwtClaimsSet.builder();
            setupAccessTokenContext(memberId, claimsBuilder, Optional.empty(), false);

            // when
            tokenCustomizer.customize(context);

            // then
            assertThat(claimsBuilder.build().<String>getClaim("provider")).isEqualTo("local");
        }

        @Test
        void otherTokenType_doesNotCustomize() {
            // given
            doReturn(new OAuth2TokenType("id_token")).when(context).getTokenType();

            // when
            tokenCustomizer.customize(context);

            // then
            verify(context, never()).getClaims();
        }

        private void setupAccessTokenContext(UUID memberId, JwtClaimsSet.Builder claimsBuilder,
                                             Optional<UUID> householdId, boolean admin) {
            doReturn(OAuth2TokenType.ACCESS_TOKEN).when(context).getTokenType();
            doReturn(authentication).when(context).getPrincipal();
            doReturn(memberId.toString()).when(authentication).getName();
            doReturn(claimsBuilder).when(context).getClaims();
            doReturn(householdId).when(memberQuery).findHouseholdIdByMemberId(memberId);
            doReturn(admin).when(memberQuery).isAdmin(memberId);
        }
    }
}
