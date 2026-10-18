package eu.wiegandt.librehousehold.session.controller;

import eu.wiegandt.librehousehold.household.LocalLoginProperties;
import eu.wiegandt.librehousehold.config.SocialLoginContextStore;
import eu.wiegandt.librehousehold.config.SocialLoginProperties;
import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderConfig;
import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderType;
import eu.wiegandt.librehousehold.household.AccountOidcPrincipal;
import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.ExternalIdentity;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.household.PasswordReset;
import eu.wiegandt.librehousehold.household.PendingSocialRegistrationStore;
import eu.wiegandt.librehousehold.model.AuthDiscovery;
import eu.wiegandt.librehousehold.model.CurrentUser;
import eu.wiegandt.librehousehold.model.Household;
import eu.wiegandt.librehousehold.model.Member;
import eu.wiegandt.librehousehold.model.PasswordResetConfirm;
import eu.wiegandt.librehousehold.model.PasswordResetRequest;
import eu.wiegandt.librehousehold.model.PendingSocialRegistration;
import eu.wiegandt.librehousehold.model.SocialLoginContext;
import eu.wiegandt.librehousehold.model.SocialLoginContext.ContextEnum;
import eu.wiegandt.librehousehold.model.SocialLoginProvider;
import eu.wiegandt.librehousehold.model.UserPreferences;
import eu.wiegandt.librehousehold.session.exception.InviteTokenIsRequiredException;
import eu.wiegandt.librehousehold.session.exception.NoAuthenticatedSessionException;
import eu.wiegandt.librehousehold.session.exception.PendingSocialRegistrationNotFoundException;
import eu.wiegandt.librehousehold.usersettings.PreferencesQuery;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SessionApiDelegateImplTest {

    @Mock
    private MemberQuery memberQuery;

    @Mock
    private HouseholdQuery householdQuery;

    @Mock
    private PreferencesQuery preferencesQuery;

    @Mock
    private PasswordReset passwordReset;

    @Mock
    private SocialLoginContextStore socialLoginContextStore;

    @Mock
    private PendingSocialRegistrationStore pendingSocialRegistrationStore;

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
            doReturn(false).when(memberQuery).isEmailVerified(member.getId());
            doReturn(true).when(memberQuery).isSocialLoginOnly(member.getId());
            var expected = new CurrentUser(member, household, preferences, false, true);

            // when
            var result = delegate.getCurrentUser();

            // then
            assertThat(result.getBody()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void nonOidcPrincipal_throwsNoAuthenticatedSessionException() {
            // given
            var principal = new AccountPrincipal("someone@example.com", "hash", true);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of()));

            // when
            // then
            assertThatThrownBy(delegate::getCurrentUser).isInstanceOf(NoAuthenticatedSessionException.class);
        }
    }

    @Nested
    class requestPasswordReset {

        @Test
        void anyEmail_delegatesAndAlwaysReturns202() {
            // given
            var email = "max@example.com";

            // when
            var result = delegate.requestPasswordReset(new PasswordResetRequest(email));

            // then
            verify(passwordReset).requestPasswordReset(email);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }
    }

    @Nested
    class confirmPasswordReset {

        @Test
        void validToken_resetsPasswordAndReturns204() {
            // given
            var token = UUID.randomUUID();
            var newPassword = "new correct horse battery staple";

            // when
            var result = delegate.confirmPasswordReset(new PasswordResetConfirm(token, newPassword));

            // then
            verify(passwordReset).confirmPasswordReset(token, newPassword);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        void invalidToken_propagatesException() {
            // given
            var token = UUID.randomUUID();
            var newPassword = "new correct horse battery staple";
            doThrow(new IllegalStateException("token invalid")).when(passwordReset).confirmPasswordReset(token, newPassword);

            // when / then
            assertThatThrownBy(() -> delegate.confirmPasswordReset(new PasswordResetConfirm(token, newPassword)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class storeSocialLoginContext {

        static List<SocialLoginContext> validContexts() {
            return List.of(
                    new SocialLoginContext(ContextEnum.LOGIN),
                    new SocialLoginContext(ContextEnum.SETUP),
                    new SocialLoginContext(ContextEnum.INVITE).inviteToken(UUID.randomUUID()));
        }

        @ParameterizedTest
        @MethodSource("validContexts")
        void validContext_storesContextAndReturns204(SocialLoginContext context) {
            // when
            var result = delegate.storeSocialLoginContext(context);

            // then
            verify(socialLoginContextStore).store(context);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        void inviteContextWithoutToken_throwsInviteTokenIsRequiredExceptionWithoutStoring() {
            // given
            var context = new SocialLoginContext(ContextEnum.INVITE);

            // when / then
            assertThatThrownBy(() -> delegate.storeSocialLoginContext(context))
                    .isInstanceOf(InviteTokenIsRequiredException.class);
            verifyNoInteractions(socialLoginContextStore);
        }
    }

    @Nested
    class getAuthDiscovery {

        static List<Arguments> configuredLoginMethods() {
            var googleAndDex = new LinkedHashMap<String, ProviderConfig>();
            googleAndDex.put("google", providerWithDisplayName("Google"));
            googleAndDex.put("dex", providerWithDisplayName(null));
            return List.of(
                    argumentSet("social login enabled with two providers, one without display name",
                            new SocialLoginProperties(true, googleAndDex),
                            new LocalLoginProperties(true),
                            new AuthDiscovery(true, List.of(
                                    new SocialLoginProvider("google", "Google"),
                                    new SocialLoginProvider("dex", "dex")))),
                    argumentSet("social login disabled although a provider is configured",
                            new SocialLoginProperties(false, Map.of("google", providerWithDisplayName("Google"))),
                            new LocalLoginProperties(true),
                            new AuthDiscovery(true, List.of())),
                    argumentSet("local login disabled",
                            new SocialLoginProperties(false, Map.of()),
                            new LocalLoginProperties(false),
                            new AuthDiscovery(false, List.of())),
                    argumentSet("social login enabled with a blank display name",
                            new SocialLoginProperties(true, Map.of("dex", providerWithDisplayName("   "))),
                            new LocalLoginProperties(true),
                            new AuthDiscovery(true, List.of(new SocialLoginProvider("dex", "dex")))));
        }

        @ParameterizedTest
        @MethodSource("configuredLoginMethods")
        void configuredLoginMethods_returnsMatchingAuthDiscovery(SocialLoginProperties socialLoginProperties,
                                                                LocalLoginProperties localLoginProperties,
                                                                AuthDiscovery expected) {
            // given
            var discoveryDelegate = new SessionApiDelegateImpl(memberQuery, householdQuery, preferencesQuery,
                    passwordReset, socialLoginContextStore, socialLoginProperties, localLoginProperties,
                    pendingSocialRegistrationStore);

            // when
            var result = discoveryDelegate.getAuthDiscovery();

            // then
            assertThat(result.getBody()).usingRecursiveComparison().isEqualTo(expected);
        }

        private static ProviderConfig providerWithDisplayName(String displayName) {
            return new ProviderConfig(ProviderType.OIDC, null, "client-id", "client-secret", displayName,
                    null, null, null, null, List.of());
        }
    }

    @Nested
    class getPendingSocialRegistration {

        static List<Arguments> pendingRegistrations() {
            return List.of(
                    argumentSet("provider reported a name",
                            new ExternalIdentity("google", "external-subject", "max@example.com", "Max Mustermann"),
                            new PendingSocialRegistration("max@example.com").name("Max Mustermann")),
                    argumentSet("provider reported no name",
                            new ExternalIdentity("google", "external-subject", "max@example.com", null),
                            new PendingSocialRegistration("max@example.com")));
        }

        @ParameterizedTest
        @MethodSource("pendingRegistrations")
        void pendingRegistration_returnsNameAndEmail(ExternalIdentity identity, PendingSocialRegistration expected) {
            // given
            doReturn(Optional.of(identity)).when(pendingSocialRegistrationStore).find();

            // when
            var result = delegate.getPendingSocialRegistration();

            // then
            assertThat(result.getBody()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void noPendingRegistration_throwsPendingSocialRegistrationNotFoundException() {
            // given
            doReturn(Optional.empty()).when(pendingSocialRegistrationStore).find();

            // when / then
            assertThatThrownBy(delegate::getPendingSocialRegistration)
                    .isInstanceOf(PendingSocialRegistrationNotFoundException.class);
        }
    }

    private DefaultOidcUser oidcUserWithSubject(String email) {
        var idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(300),
                Map.of(IdTokenClaimNames.SUB, email, IdTokenClaimNames.ISS, "http://localhost/issuer"));
        return new DefaultOidcUser(List.of(), idToken);
    }
}
