package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.auth.model.FederatedIdentityEntity;
import eu.wiegandt.librehousehold.auth.repository.FederatedIdentityRepository;
import eu.wiegandt.librehousehold.household.MemberQuery;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    FederatedIdentityRepository federatedIdentityRepository;

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
            setupLocalAccessTokenContext(memberId, claimsBuilder, Optional.empty(), false);

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
            setupLocalAccessTokenContext(memberId, claimsBuilder, Optional.of(householdId), false);

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
            setupLocalAccessTokenContext(memberId, claimsBuilder, Optional.empty(), false);

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
            setupLocalAccessTokenContext(memberId, claimsBuilder, Optional.empty(), true);

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
            setupLocalAccessTokenContext(memberId, claimsBuilder, Optional.empty(), false);

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
            setupLocalAccessTokenContext(memberId, claimsBuilder, Optional.empty(), false);

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

        @Test
        void socialLogin_resolvesMemberIdFromFederatedIdentity() {
            // given
            var accountId = UUID.randomUUID();
            var identity = new FederatedIdentityEntity(UUID.randomUUID(), accountId, "google", "google-sub-123");
            var claimsBuilder = JwtClaimsSet.builder();
            var socialAuth = buildOAuth2AuthenticationToken("google-sub-123", "google");
            doReturn(OAuth2TokenType.ACCESS_TOKEN).when(context).getTokenType();
            doReturn(socialAuth).when(context).getPrincipal();
            doReturn(claimsBuilder).when(context).getClaims();
            doReturn(Optional.of(identity)).when(federatedIdentityRepository)
                    .findByProviderAndProviderSub("google", "google-sub-123");
            doReturn(Optional.empty()).when(memberQuery).findHouseholdIdByMemberId(accountId);
            doReturn(false).when(memberQuery).isAdmin(accountId);

            // when
            tokenCustomizer.customize(context);

            // then
            assertThat(claimsBuilder.build().<String>getClaim("sub")).isEqualTo(accountId.toString());
        }

        @Test
        void socialLogin_addsGoogleProviderClaim() {
            // given
            var accountId = UUID.randomUUID();
            var identity = new FederatedIdentityEntity(UUID.randomUUID(), accountId, "google", "google-sub-456");
            var claimsBuilder = JwtClaimsSet.builder();
            var socialAuth = buildOAuth2AuthenticationToken("google-sub-456", "google");
            doReturn(OAuth2TokenType.ACCESS_TOKEN).when(context).getTokenType();
            doReturn(socialAuth).when(context).getPrincipal();
            doReturn(claimsBuilder).when(context).getClaims();
            doReturn(Optional.of(identity)).when(federatedIdentityRepository)
                    .findByProviderAndProviderSub("google", "google-sub-456");
            doReturn(Optional.empty()).when(memberQuery).findHouseholdIdByMemberId(accountId);
            doReturn(false).when(memberQuery).isAdmin(accountId);

            // when
            tokenCustomizer.customize(context);

            // then
            assertThat(claimsBuilder.build().<String>getClaim("provider")).isEqualTo("google");
        }

        private void setupLocalAccessTokenContext(UUID memberId, JwtClaimsSet.Builder claimsBuilder,
                                                  Optional<UUID> householdId, boolean admin) {
            doReturn(OAuth2TokenType.ACCESS_TOKEN).when(context).getTokenType();
            doReturn(authentication).when(context).getPrincipal();
            doReturn(memberId.toString()).when(authentication).getName();
            doReturn(claimsBuilder).when(context).getClaims();
            doReturn(householdId).when(memberQuery).findHouseholdIdByMemberId(memberId);
            doReturn(admin).when(memberQuery).isAdmin(memberId);
        }

        private OAuth2AuthenticationToken buildOAuth2AuthenticationToken(String sub, String registrationId) {
            var idToken = new OidcIdToken(
                    "raw-token",
                    Instant.now(),
                    Instant.now().plusSeconds(60),
                    Map.of("sub", sub, "iss", "https://accounts.google.com", "aud", List.of("client")));
            var principal = new DefaultOidcUser(List.of(), idToken);
            return new OAuth2AuthenticationToken(principal, List.of(), registrationId);
        }
    }
}
