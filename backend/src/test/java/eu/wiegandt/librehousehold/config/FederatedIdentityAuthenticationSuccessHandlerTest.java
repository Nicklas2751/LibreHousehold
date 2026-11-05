package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.ExternalIdentity;
import eu.wiegandt.librehousehold.household.AccountSignIn;
import eu.wiegandt.librehousehold.household.PendingSocialRegistrationStore;
import eu.wiegandt.librehousehold.model.SocialLoginContext;
import eu.wiegandt.librehousehold.model.SocialLoginContext.ContextEnum;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FederatedIdentityAuthenticationSuccessHandlerTest {

    private static final String PROVIDER_ID = "dev-provider";
    private static final String EXTERNAL_SUBJECT = "external-subject";
    private static final UUID MEMBER_ID = UUID.fromString("c9a4b5d6-e7f8-9012-cdef-ab3456789012");
    private static final String PROVIDER_EMAIL = "provider@example.com";
    private static final UUID INVITE_TOKEN = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private AccountSignIn accountSignIn;

    @Mock
    private SocialLoginContextStore socialLoginContextStore;

    @Mock
    private PendingSocialRegistrationStore pendingSocialRegistrationStore;

    @Mock
    private OAuth2AuthorizedClientRepository authorizedClientRepository;

    @InjectMocks
    private FederatedIdentityAuthenticationSuccessHandler successHandler;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class onAuthenticationSuccess {

        @Test
        void linkedIdentity_signsInMember() throws Exception {
            // given
            doReturn(new SocialLoginContext(ContextEnum.LOGIN)).when(socialLoginContextStore).consume();
            doReturn(Optional.of(MEMBER_ID)).when(accountSignIn).signInByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    externalAuthentication());

            // then
            verify(accountSignIn).signInByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);
        }

        @Test
        void linkedIdentity_redirectsToDashboard() throws Exception {
            // given
            var response = new MockHttpServletResponse();
            doReturn(new SocialLoginContext(ContextEnum.LOGIN)).when(socialLoginContextStore).consume();
            doReturn(Optional.of(MEMBER_ID)).when(accountSignIn).signInByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, externalAuthentication());

            // then
            assertThat(response.getRedirectedUrl()).isEqualTo("/app/dashboard");
        }

        @Test
        void noLinkedIdentity_invalidatesSession() throws Exception {
            // given
            var request = new MockHttpServletRequest();
            request.getSession();
            doReturn(new SocialLoginContext(ContextEnum.LOGIN)).when(socialLoginContextStore).consume();
            doReturn(Optional.empty()).when(accountSignIn).signInByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(), externalAuthentication());

            // then
            assertThat(request.getSession(false)).isNull();
        }

        @Test
        void noLinkedIdentity_redirectsWithNoAccountReason() throws Exception {
            // given
            var response = new MockHttpServletResponse();
            doReturn(new SocialLoginContext(ContextEnum.LOGIN)).when(socialLoginContextStore).consume();
            doReturn(Optional.empty()).when(accountSignIn).signInByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, externalAuthentication());

            // then
            assertThat(response.getRedirectedUrl()).isEqualTo("/login?error&reason=social-login-no-account");
        }

        @Test
        void noLinkedIdentity_clearsExternalAuthentication() throws Exception {
            // given
            var externalAuthentication = externalAuthentication();
            SecurityContextHolder.getContext().setAuthentication(externalAuthentication);
            doReturn(new SocialLoginContext(ContextEnum.LOGIN)).when(socialLoginContextStore).consume();
            doReturn(Optional.empty()).when(accountSignIn).signInByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), externalAuthentication);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void signInThrows_invalidatesSession() throws Exception {
            // given
            var request = new MockHttpServletRequest();
            request.getSession();
            doReturn(new SocialLoginContext(ContextEnum.LOGIN)).when(socialLoginContextStore).consume();
            doThrow(new IllegalStateException("database down")).when(accountSignIn)
                    .signInByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(), externalAuthentication());

            // then
            assertThat(request.getSession(false)).isNull();
        }

        @Test
        void signInThrows_redirectsWithSocialLoginFailedReason() throws Exception {
            // given
            var response = new MockHttpServletResponse();
            doReturn(new SocialLoginContext(ContextEnum.LOGIN)).when(socialLoginContextStore).consume();
            doThrow(new IllegalStateException("database down")).when(accountSignIn)
                    .signInByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, externalAuthentication());

            // then
            assertThat(response.getRedirectedUrl()).isEqualTo("/login?error&reason=social-login-failed");
        }

        static List<Arguments> allContexts() {
            return List.of(
                    argumentSet("login", new SocialLoginContext(ContextEnum.LOGIN)),
                    argumentSet("household setup", new SocialLoginContext(ContextEnum.SETUP)),
                    argumentSet("invite join", new SocialLoginContext(ContextEnum.INVITE).inviteToken(INVITE_TOKEN)));
        }

        @ParameterizedTest
        @MethodSource("allContexts")
        void anyContext_removesAuthorizedClient(SocialLoginContext context) throws Exception {
            // given
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            var externalAuthentication = externalAuthentication(verifiedOidcUser());
            doReturn(context).when(socialLoginContextStore).consume();
            lenient().doReturn(Optional.of(MEMBER_ID)).when(accountSignIn).signInByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(request, response, externalAuthentication);

            // then
            verify(authorizedClientRepository).removeAuthorizedClient(PROVIDER_ID, externalAuthentication, request, response);
        }
    }

    @Nested
    class onAuthenticationSuccessWithRegistrationContext {

        static List<Arguments> registrationContexts() {
            return List.of(
                    argumentSet("household setup", new SocialLoginContext(ContextEnum.SETUP), "/setup?social"),
                    argumentSet("invite join", new SocialLoginContext(ContextEnum.INVITE).inviteToken(INVITE_TOKEN),
                            "/invite/" + INVITE_TOKEN + "?social"));
        }

        @ParameterizedTest
        @MethodSource("registrationContexts")
        void verifiedEmail_redirectsBackToRegistrationPage(SocialLoginContext context, String expectedRedirect)
                throws Exception {
            // given
            var response = new MockHttpServletResponse();
            doReturn(context).when(socialLoginContextStore).consume();

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                    externalAuthentication(verifiedOidcUser()));

            // then
            assertThat(response.getRedirectedUrl()).isEqualTo(expectedRedirect);
        }

        static List<Arguments> verifiedUsers() {
            return List.of(
                    argumentSet("OIDC user with name", verifiedOidcUser(),
                            new ExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT, PROVIDER_EMAIL, "Max Mustermann")),
                    argumentSet("OAuth2 user without name", new DefaultOAuth2User(List.of(),
                                    Map.of("id", EXTERNAL_SUBJECT, "email", PROVIDER_EMAIL, "email_verified", true), "id"),
                            new ExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT, PROVIDER_EMAIL, null)));
        }

        @ParameterizedTest
        @MethodSource("verifiedUsers")
        void verifiedEmail_storesExternalIdentityAsPendingRegistration(OAuth2User externalUser, ExternalIdentity expected)
                throws Exception {
            // given
            doReturn(new SocialLoginContext(ContextEnum.SETUP)).when(socialLoginContextStore).consume();

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    externalAuthentication(externalUser));

            // then
            verify(pendingSocialRegistrationStore).store(expected);
        }

        /**
         * An external subject is no member, so it must never stay authenticated in the session.
         */
        @Test
        void verifiedEmail_removesExternalAuthenticationFromSession() throws Exception {
            // given
            var request = new MockHttpServletRequest();
            var externalAuthentication = externalAuthentication(verifiedOidcUser());
            var externalContext = new SecurityContextImpl(externalAuthentication);
            request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, externalContext);
            doReturn(new SocialLoginContext(ContextEnum.SETUP)).when(socialLoginContextStore).consume();

            // when
            successHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(), externalAuthentication);

            // then
            assertThat(request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                    .isNull();
        }

        @Test
        void verifiedEmail_clearsExternalAuthenticationFromSecurityContext() throws Exception {
            // given
            var externalAuthentication = externalAuthentication(verifiedOidcUser());
            SecurityContextHolder.getContext().setAuthentication(externalAuthentication);
            doReturn(new SocialLoginContext(ContextEnum.SETUP)).when(socialLoginContextStore).consume();

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    externalAuthentication);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        /**
         * The pending registration lives in the session, so it must survive the callback.
         */
        @Test
        void verifiedEmail_keepsSession() throws Exception {
            // given
            var request = new MockHttpServletRequest();
            var session = (MockHttpSession) request.getSession();
            doReturn(new SocialLoginContext(ContextEnum.SETUP)).when(socialLoginContextStore).consume();

            // when
            successHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(),
                    externalAuthentication(verifiedOidcUser()));

            // then
            assertThat(session.isInvalid()).isFalse();
        }

        static List<Arguments> usersWithoutVerifiedEmail() {
            return List.of(
                    argumentSet("e-mail not verified", new DefaultOAuth2User(List.of(),
                            Map.of("id", EXTERNAL_SUBJECT, "email", PROVIDER_EMAIL, "email_verified", false), "id")),
                    argumentSet("verification status missing", new DefaultOAuth2User(List.of(),
                            Map.of("id", EXTERNAL_SUBJECT, "email", PROVIDER_EMAIL), "id")),
                    argumentSet("verified without e-mail", new DefaultOAuth2User(List.of(),
                            Map.of("id", EXTERNAL_SUBJECT, "email_verified", true), "id")));
        }

        @ParameterizedTest
        @MethodSource("usersWithoutVerifiedEmail")
        void noVerifiedEmail_redirectsToSetupWithEmailUnverifiedReason(OAuth2User externalUser) throws Exception {
            // given
            var response = new MockHttpServletResponse();
            doReturn(new SocialLoginContext(ContextEnum.SETUP)).when(socialLoginContextStore).consume();

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, externalAuthentication(externalUser));

            // then
            assertThat(response.getRedirectedUrl()).isEqualTo("/setup?error&reason=social-email-unverified");
        }

        @Test
        void inviteContextWithoutVerifiedEmail_redirectsToInviteWithEmailUnverifiedReason() throws Exception {
            // given
            var response = new MockHttpServletResponse();
            doReturn(new SocialLoginContext(ContextEnum.INVITE).inviteToken(INVITE_TOKEN)).when(socialLoginContextStore).consume();

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                    externalAuthentication(new DefaultOAuth2User(List.of(), Map.of("id", EXTERNAL_SUBJECT), "id")));

            // then
            assertThat(response.getRedirectedUrl())
                    .isEqualTo("/invite/" + INVITE_TOKEN + "?error&reason=social-email-unverified");
        }

        @Test
        void noVerifiedEmail_storesNoPendingRegistration() throws Exception {
            // given
            doReturn(new SocialLoginContext(ContextEnum.SETUP)).when(socialLoginContextStore).consume();

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    externalAuthentication(new DefaultOAuth2User(List.of(), Map.of("id", EXTERNAL_SUBJECT), "id")));

            // then
            verifyNoInteractions(pendingSocialRegistrationStore);
        }

        @Test
        void noVerifiedEmail_invalidatesSession() throws Exception {
            // given
            var request = new MockHttpServletRequest();
            request.getSession();
            doReturn(new SocialLoginContext(ContextEnum.SETUP)).when(socialLoginContextStore).consume();

            // when
            successHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(),
                    externalAuthentication(new DefaultOAuth2User(List.of(), Map.of("id", EXTERNAL_SUBJECT), "id")));

            // then
            assertThat(request.getSession(false)).isNull();
        }

        @Test
        void verifiedEmail_resolvesNoLinkedMember() throws Exception {
            // given
            doReturn(new SocialLoginContext(ContextEnum.SETUP)).when(socialLoginContextStore).consume();

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    externalAuthentication(verifiedOidcUser()));

            // then
            verifyNoInteractions(accountSignIn);
        }
    }

    private static OidcUser verifiedOidcUser() {
        var idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(300), Map.of(
                IdTokenClaimNames.SUB, EXTERNAL_SUBJECT,
                StandardClaimNames.EMAIL, PROVIDER_EMAIL,
                StandardClaimNames.EMAIL_VERIFIED, true,
                StandardClaimNames.NAME, "Max Mustermann"));
        return new DefaultOidcUser(List.of(), idToken);
    }

    private OAuth2AuthenticationToken externalAuthentication() {
        var externalUser = new DefaultOAuth2User(List.of(), Map.of("sub", EXTERNAL_SUBJECT), "sub");
        return new OAuth2AuthenticationToken(externalUser, List.of(), PROVIDER_ID);
    }

    private OAuth2AuthenticationToken externalAuthentication(OAuth2User externalUser) {
        return new OAuth2AuthenticationToken(externalUser, List.of(), PROVIDER_ID);
    }
}
