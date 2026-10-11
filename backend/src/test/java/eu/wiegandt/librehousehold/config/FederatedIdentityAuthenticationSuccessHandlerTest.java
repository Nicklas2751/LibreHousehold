package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.SocialLoginContext;
import eu.wiegandt.librehousehold.model.SocialLoginContext.ContextEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class FederatedIdentityAuthenticationSuccessHandlerTest {

    private static final String PROVIDER_ID = "dev-provider";
    private static final String EXTERNAL_SUBJECT = "external-subject";
    private static final String MEMBER_EMAIL = "member@example.com";

    @Mock
    private MemberQuery memberQuery;

    @Mock
    private SocialLoginContextStore socialLoginContextStore;

    @InjectMocks
    private FederatedIdentityAuthenticationSuccessHandler successHandler;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class onAuthenticationSuccess {

        @Test
        void linkedIdentity_redirectsToSpaContinuation() throws Exception {
            // given
            var response = new MockHttpServletResponse();
            doReturn(new SocialLoginContext(ContextEnum.LOGIN)).when(socialLoginContextStore).consume();
            doReturn(Optional.of(MEMBER_EMAIL)).when(memberQuery).findEmailByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, externalAuthentication());

            // then
            assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorization/spa-backend-client");
        }

        @Test
        void linkedIdentity_authenticatesMemberEmailWithAuthorizationCodeFactor() throws Exception {
            // given
            doReturn(new SocialLoginContext(ContextEnum.LOGIN)).when(socialLoginContextStore).consume();
            doReturn(Optional.of(MEMBER_EMAIL)).when(memberQuery).findEmailByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);
            var expected = new SessionAuthentication(MEMBER_EMAIL, true, List.of(FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY));

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), externalAuthentication());

            // then
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            var result = new SessionAuthentication(authentication.getName(), authentication.isAuthenticated(),
                    authentication.getAuthorities().stream()
                            .filter(FactorGrantedAuthority.class::isInstance)
                            .map(GrantedAuthority::getAuthority)
                            .toList());
            assertThat(result).isEqualTo(expected);
        }

        @Test
        void linkedIdentity_persistsMemberAuthenticationInHttpSession() throws Exception {
            // given
            var request = new MockHttpServletRequest();
            doReturn(new SocialLoginContext(ContextEnum.LOGIN)).when(socialLoginContextStore).consume();
            doReturn(Optional.of(MEMBER_EMAIL)).when(memberQuery).findEmailByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(), externalAuthentication());

            // then
            var persistedContext = request.getSession()
                    .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            assertThat(persistedContext).isEqualTo(SecurityContextHolder.getContext());
        }

        @Test
        void noLinkedIdentity_redirectsToLoginWithNoAccountReason() throws Exception {
            // given
            var response = new MockHttpServletResponse();
            doReturn(new SocialLoginContext(ContextEnum.LOGIN)).when(socialLoginContextStore).consume();
            doReturn(Optional.empty()).when(memberQuery).findEmailByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

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
            doReturn(Optional.empty()).when(memberQuery).findEmailByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), externalAuthentication);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void noLinkedIdentity_invalidatesSession() throws Exception {
            // given
            var request = new MockHttpServletRequest();
            request.getSession();
            doReturn(new SocialLoginContext(ContextEnum.LOGIN)).when(socialLoginContextStore).consume();
            doReturn(Optional.empty()).when(memberQuery).findEmailByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(), externalAuthentication());

            // then
            assertThat(request.getSession(false)).isNull();
        }

        @ParameterizedTest
        @EnumSource(value = ContextEnum.class, names = {"SETUP", "INVITE"})
        void registrationContextWithLinkedIdentity_redirectsToLoginWithNoAccountReason(ContextEnum context) throws Exception {
            // given — lenient: a registration context must be rejected without even resolving the identity
            var response = new MockHttpServletResponse();
            doReturn(new SocialLoginContext(context)).when(socialLoginContextStore).consume();
            lenient().doReturn(Optional.of(MEMBER_EMAIL)).when(memberQuery).findEmailByExternalIdentity(PROVIDER_ID, EXTERNAL_SUBJECT);

            // when
            successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, externalAuthentication());

            // then
            assertThat(response.getRedirectedUrl()).isEqualTo("/login?error&reason=social-login-no-account");
        }
    }

    private OAuth2AuthenticationToken externalAuthentication() {
        var externalUser = new DefaultOAuth2User(List.of(), Map.of("sub", EXTERNAL_SUBJECT), "sub");
        return new OAuth2AuthenticationToken(externalUser, List.of(), PROVIDER_ID);
    }

    private record SessionAuthentication(String name, boolean authenticated, List<String> factorAuthorities) {
    }
}
