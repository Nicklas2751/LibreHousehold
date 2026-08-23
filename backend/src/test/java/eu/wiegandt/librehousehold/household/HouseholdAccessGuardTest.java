package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class HouseholdAccessGuardTest {

    private static final String CLIENT_REGISTRATION_ID = "spa-backend-client";

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private HouseholdAccessGuard guard;

    @Nested
    class isMember {

        @Test
        void principalBelongsToHousehold_true() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var authentication = authenticationOf(memberId, UUID.randomUUID(), false);
            doReturn(true).when(memberRepository).existsByIdAndHouseholdId(memberId, householdId);

            // when
            var result = guard.isMember(householdId, authentication);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void principalBelongsToDifferentHousehold_false() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var authentication = authenticationOf(memberId, UUID.randomUUID(), false);
            doReturn(false).when(memberRepository).existsByIdAndHouseholdId(memberId, householdId);

            // when
            var result = guard.isMember(householdId, authentication);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class isAdminOfHousehold {

        @Test
        void adminOfThisHouseholdInDatabase_true() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var authentication = authenticationOf(memberId, householdId, true);
            doReturn(true).when(memberRepository).existsByIdAndHouseholdIdAndIsAdminTrue(memberId, householdId);

            // when
            var result = guard.isAdminOfHousehold(householdId, authentication);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void memberButNotAdminInDatabase_false() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var authentication = authenticationOf(memberId, householdId, false);
            doReturn(false).when(memberRepository).existsByIdAndHouseholdIdAndIsAdminTrue(memberId, householdId);

            // when
            var result = guard.isAdminOfHousehold(householdId, authentication);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void adminButNotMemberOfThisHousehold_false() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var authentication = authenticationOf(memberId, UUID.randomUUID(), true);
            doReturn(false).when(memberRepository).existsByIdAndHouseholdIdAndIsAdminTrue(memberId, householdId);

            // when
            var result = guard.isAdminOfHousehold(householdId, authentication);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void staleAdminClaimButRevokedInDatabase_false() {
            // given — the OIDC session still carries the isAdmin=true claim from login time, but the
            // admin right was revoked in the database afterwards (e.g. via transferOwnership)
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var authentication = authenticationOf(memberId, householdId, true);
            doReturn(false).when(memberRepository).existsByIdAndHouseholdIdAndIsAdminTrue(memberId, householdId);

            // when
            var result = guard.isAdminOfHousehold(householdId, authentication);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class isSelf {

        @Test
        void sameMemberId_true() {
            // given
            var memberId = UUID.randomUUID();
            var authentication = authenticationOf(memberId, UUID.randomUUID(), false);

            // when
            var result = guard.isSelf(memberId, authentication);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void differentMemberId_false() {
            // given
            var memberId = UUID.randomUUID();
            var authentication = authenticationOf(memberId, UUID.randomUUID(), false);

            // when
            var result = guard.isSelf(UUID.randomUUID(), authentication);

            // then
            assertThat(result).isFalse();
        }
    }

    private OAuth2AuthenticationToken authenticationOf(UUID memberId, UUID householdId, boolean isAdmin) {
        var principal = new AccountOidcPrincipal(oidcUser(), memberId, householdId, isAdmin);
        return new OAuth2AuthenticationToken(principal, List.of(), CLIENT_REGISTRATION_ID);
    }

    private OidcUser oidcUser() {
        var idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(300),
                Map.of(IdTokenClaimNames.SUB, "max@example.com", IdTokenClaimNames.ISS, "http://localhost/issuer"));
        return new DefaultOidcUser(List.of(), idToken);
    }
}
