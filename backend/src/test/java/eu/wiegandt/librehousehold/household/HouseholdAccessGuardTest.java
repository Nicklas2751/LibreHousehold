package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class HouseholdAccessGuardTest {

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
            var authentication = authenticationOf(memberId, UUID.randomUUID());
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
            var authentication = authenticationOf(memberId, UUID.randomUUID());
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
            var authentication = authenticationOf(memberId, householdId);
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
            var authentication = authenticationOf(memberId, householdId);
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
            var authentication = authenticationOf(memberId, UUID.randomUUID());
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
            var authentication = authenticationOf(memberId, householdId);
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
            var authentication = authenticationOf(memberId, UUID.randomUUID());

            // when
            var result = guard.isSelf(memberId, authentication);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void differentMemberId_false() {
            // given
            var memberId = UUID.randomUUID();
            var authentication = authenticationOf(memberId, UUID.randomUUID());

            // when
            var result = guard.isSelf(UUID.randomUUID(), authentication);

            // then
            assertThat(result).isFalse();
        }
    }

    private UsernamePasswordAuthenticationToken authenticationOf(UUID memberId, UUID householdId) {
        var principal = new AccountPrincipal(memberId, householdId, null, true);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }
}
