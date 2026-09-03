package eu.wiegandt.librehousehold.session.controller;

import eu.wiegandt.librehousehold.household.AccountOidcPrincipal;
import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.CurrentUser;
import eu.wiegandt.librehousehold.model.Household;
import eu.wiegandt.librehousehold.model.Member;
import eu.wiegandt.librehousehold.model.UserPreferences;
import eu.wiegandt.librehousehold.session.exception.NoAuthenticatedSessionException;
import eu.wiegandt.librehousehold.usersettings.PreferencesQuery;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class SessionApiDelegateImplTest {

    @Mock
    private MemberQuery memberQuery;

    @Mock
    private HouseholdQuery householdQuery;

    @Mock
    private PreferencesQuery preferencesQuery;

    @InjectMocks
    private SessionApiDelegateImpl delegate;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class getCurrentUser {

        @Test
        void authenticatedPrincipal_returnsCurrentUserWithHouseholdFromHouseholdQuery() {
            // given
            var member = Instancio.create(Member.class);
            var householdId = UUID.randomUUID();
            var household = Instancio.create(Household.class);
            var preferences = Instancio.create(UserPreferences.class);
            var principal = new AccountOidcPrincipal(oidcUserWithSubject(member.getEmail()), member.getId(), householdId, true);
            SecurityContextHolder.getContext().setAuthentication(
                    new OAuth2AuthenticationToken(principal, List.of(), "spa-backend-client"));
            doReturn(member).when(memberQuery).getMember(member.getId());
            doReturn(household).when(householdQuery).getHousehold(householdId);
            doReturn(preferences).when(preferencesQuery).getPreferencesOrDefault(member.getId());
            var expected = new CurrentUser(member, household, preferences);

            // when
            var result = delegate.getCurrentUser();

            // then
            assertThat(result.getBody()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void nonOidcPrincipal_throwsNoAuthenticatedSessionException() {
            // given
            var principal = new AccountPrincipal("someone@example.com", "hash");
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of()));

            // when
            // then
            assertThatThrownBy(delegate::getCurrentUser).isInstanceOf(NoAuthenticatedSessionException.class);
        }
    }

    private DefaultOidcUser oidcUserWithSubject(String email) {
        var idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(300),
                Map.of(IdTokenClaimNames.SUB, email, IdTokenClaimNames.ISS, "http://localhost/issuer"));
        return new DefaultOidcUser(List.of(), idToken);
    }
}
