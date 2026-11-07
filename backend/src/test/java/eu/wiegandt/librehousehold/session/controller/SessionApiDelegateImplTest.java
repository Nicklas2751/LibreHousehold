package eu.wiegandt.librehousehold.session.controller;

import eu.wiegandt.librehousehold.household.LocalLoginProperties;
import eu.wiegandt.librehousehold.config.SocialLoginContextStore;
import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.ExternalIdentity;
import eu.wiegandt.librehousehold.household.LocalLoginDisabledException;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
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

import java.time.Duration;
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
            var principal = new AccountPrincipal(member.getId(), householdId, null, true);
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
            doReturn(member).when(memberQuery).getMember(member.getId());
            doReturn(household).when(householdQuery).getHousehold(householdId);
            doReturn(preferences).when(preferencesQuery).getPreferencesOrDefault(member.getId());
            doReturn(false).when(memberQuery).isEmailVerified(member.getId());
            doReturn(true).when(memberQuery).isSocialLoginOnly(member.getId());
            var delegateWithSessionTimeout = new SessionApiDelegateImpl(memberQuery, householdQuery, preferencesQuery,
                    passwordReset, socialLoginContextStore,
                    new StaticListableBeanFactory().getBeanProvider(InMemoryClientRegistrationRepository.class),
                    new LocalLoginProperties(true), pendingSocialRegistrationStore, Duration.ofMinutes(30));
            var expected = new CurrentUser(member, household, preferences, false, true, 1800);

            // when
            var result = delegateWithSessionTimeout.getCurrentUser();

            // then
            assertThat(result.getBody()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void foreignPrincipal_throwsNoAuthenticatedSessionException() {
            // given
            var externalUser = new DefaultOAuth2User(List.of(), Map.of("sub", "external-subject"), "sub");
            SecurityContextHolder.getContext().setAuthentication(
                    new OAuth2AuthenticationToken(externalUser, List.of(), "google"));

            // when / then
            assertThatThrownBy(delegate::getCurrentUser).isInstanceOf(NoAuthenticatedSessionException.class);
        }
    }

    @Test
    void login_anyRequest_throwsLocalLoginDisabled() {
        // when / then
        assertThatThrownBy(() -> delegate.login("max@example.com", "secret"))
                .isInstanceOf(LocalLoginDisabledException.class);
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

        @Test
        void withRegistrations_listsClientNames() {
            // given
            var registrations = new InMemoryClientRegistrationRepository(
                    CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId("client-id").clientName("Google").build(),
                    ClientRegistration.withRegistrationId("dex")
                            .clientId("client-id")
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                            .authorizationUri("https://dex.example.com/dex/auth")
                            .tokenUri("https://dex.example.com/dex/token")
                            .build());
            var expected = new AuthDiscovery(true, List.of(
                    new SocialLoginProvider("google", "Google"),
                    new SocialLoginProvider("dex", "dex")));

            // when
            var result = discoveryDelegate(registrations, new LocalLoginProperties(true)).getAuthDiscovery();

            // then — the repository does not keep the configuration order
            assertThat(result.getBody()).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(expected);
        }

        @Test
        void withoutRepository_empty() {
            // given
            var expected = new AuthDiscovery(false, List.of());

            // when
            var result = discoveryDelegate(null, new LocalLoginProperties(false)).getAuthDiscovery();

            // then
            assertThat(result.getBody()).usingRecursiveComparison().isEqualTo(expected);
        }

        private SessionApiDelegateImpl discoveryDelegate(InMemoryClientRegistrationRepository registrations,
                                                         LocalLoginProperties localLoginProperties) {
            var beanFactory = new StaticListableBeanFactory();
            if (registrations != null) {
                beanFactory.addBean("clientRegistrationRepository", registrations);
            }
            return new SessionApiDelegateImpl(memberQuery, householdQuery, preferencesQuery, passwordReset,
                    socialLoginContextStore, beanFactory.getBeanProvider(InMemoryClientRegistrationRepository.class),
                    localLoginProperties, pendingSocialRegistrationStore, Duration.ofMinutes(30));
        }
    }

    @Nested
    class getPendingSocialRegistration {

        static List<Arguments> pendingRegistrations() {
            return List.of(
                    argumentSet("provider reported a name",
                            new ExternalIdentity("https://accounts.google.com", "external-subject", "max@example.com", "Max Mustermann"),
                            new PendingSocialRegistration("max@example.com").name("Max Mustermann")),
                    argumentSet("provider reported no name",
                            new ExternalIdentity("https://accounts.google.com", "external-subject", "max@example.com", null),
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
}
