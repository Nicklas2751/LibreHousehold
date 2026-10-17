package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class AccountSessionAuthenticatorTest {

    @Mock
    private AccountUserDetailsService accountUserDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearThreadLocalState() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    class authenticateAndPersistSession {

        @Test
        void unverifiedAccountWithCorrectPassword_setsAuthenticationResultInSecurityContextHolder() {
            // given — the account is unverified (isEnabled() == false): this must not matter here,
            // since this class bypasses UserDetails.isEnabled() checks entirely (see class javadoc)
            var email = "member@example.com";
            var rawPassword = "s3cret!";
            var principal = new AccountPrincipal(email, "$argon2id$...", false);
            doReturn(principal).when(accountUserDetailsService).loadUserByUsername(email);
            doReturn(true).when(passwordEncoder).matches(rawPassword, principal.passwordHash());
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
            var authenticator = new AccountSessionAuthenticator(accountUserDetailsService, passwordEncoder);

            // when
            authenticator.authenticateAndPersistSession(email, rawPassword);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(principal);
        }

        @Test
        void validCredentials_grantsPasswordFactorAuthority() {
            // given — the OIDC token generator (JwtGenerator#getAuthenticationTime) requires at
            // least one FactorGrantedAuthority to compute the auth_time claim, otherwise it throws
            // "authenticationTime cannot be null" during the subsequent authorization code exchange
            var email = "member@example.com";
            var rawPassword = "s3cret!";
            var principal = new AccountPrincipal(email, "$argon2id$...", true);
            doReturn(principal).when(accountUserDetailsService).loadUserByUsername(email);
            doReturn(true).when(passwordEncoder).matches(rawPassword, principal.passwordHash());
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
            var authenticator = new AccountSessionAuthenticator(accountUserDetailsService, passwordEncoder);

            // when
            authenticator.authenticateAndPersistSession(email, rawPassword);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .filteredOn(FactorGrantedAuthority.class::isInstance)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly(FactorGrantedAuthority.PASSWORD_AUTHORITY);
        }

        @Test
        void validCredentials_persistsSecurityContextInHttpSession() {
            // given
            var email = "member@example.com";
            var rawPassword = "s3cret!";
            var principal = new AccountPrincipal(email, "$argon2id$...", true);
            doReturn(principal).when(accountUserDetailsService).loadUserByUsername(email);
            doReturn(true).when(passwordEncoder).matches(rawPassword, principal.passwordHash());
            var request = new MockHttpServletRequest();
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes(request, new MockHttpServletResponse()));
            var authenticator = new AccountSessionAuthenticator(accountUserDetailsService, passwordEncoder);

            // when
            authenticator.authenticateAndPersistSession(email, rawPassword);

            // then
            var persistedContext = request.getSession()
                    .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            assertThat(persistedContext).isEqualTo(SecurityContextHolder.getContext());
        }

        /**
         * The SPA continues with a fresh authorization: an /oauth2/authorize request saved before
         * (the SPA's own login ran into /login first) is stale and must not be resumed afterwards.
         */
        @Test
        void validCredentials_removesStaleSavedRequest() {
            // given
            var email = "member@example.com";
            var rawPassword = "s3cret!";
            var principal = new AccountPrincipal(email, "$argon2id$...", true);
            doReturn(principal).when(accountUserDetailsService).loadUserByUsername(email);
            doReturn(true).when(passwordEncoder).matches(rawPassword, principal.passwordHash());
            var request = new MockHttpServletRequest("GET", "/oauth2/authorize");
            var response = new MockHttpServletResponse();
            var requestCache = new HttpSessionRequestCache();
            requestCache.saveRequest(request, response);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
            var authenticator = new AccountSessionAuthenticator(accountUserDetailsService, passwordEncoder);

            // when
            authenticator.authenticateAndPersistSession(email, rawPassword);

            // then
            assertThat(requestCache.getRequest(request, response)).isNull();
        }

        @Test
        void wrongPassword_throwsBadCredentialsExceptionWithoutSettingSecurityContext() {
            // given
            var email = "member@example.com";
            var rawPassword = "wrongPassword";
            var principal = new AccountPrincipal(email, "$argon2id$...", true);
            doReturn(principal).when(accountUserDetailsService).loadUserByUsername(email);
            doReturn(false).when(passwordEncoder).matches(rawPassword, principal.passwordHash());
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
            var authenticator = new AccountSessionAuthenticator(accountUserDetailsService, passwordEncoder);

            // when / then
            assertThatThrownBy(() -> authenticator.authenticateAndPersistSession(email, rawPassword))
                    .isInstanceOf(BadCredentialsException.class);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void noHttpServletResponseAvailable_throwsIllegalStateException() {
            // given
            var email = "member@example.com";
            var rawPassword = "s3cret!";
            var principal = new AccountPrincipal(email, "$argon2id$...", true);
            doReturn(principal).when(accountUserDetailsService).loadUserByUsername(email);
            doReturn(true).when(passwordEncoder).matches(rawPassword, principal.passwordHash());
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            var authenticator = new AccountSessionAuthenticator(accountUserDetailsService, passwordEncoder);

            // when / then
            assertThatIllegalStateException()
                    .isThrownBy(() -> authenticator.authenticateAndPersistSession(email, rawPassword))
                    .withMessage("No HttpServletResponse available for the current request");
        }
    }

    @Nested
    class authenticateFederatedAndPersistSession {

        @Test
        void email_authenticatesEmailWithAuthorizationCodeFactor() {
            // given — named after the e-mail, because the embedded Authorization Server issues the
            // session's name as the ID token's sub, which AccountOidcUserService resolves as e-mail
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
            var authenticator = new AccountSessionAuthenticator(accountUserDetailsService, passwordEncoder);
            var expected = new SessionAuthentication("member@example.com", true,
                    List.of(FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY));

            // when
            authenticator.authenticateFederatedAndPersistSession("member@example.com");

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
        void email_persistsSecurityContextInHttpSession() {
            // given
            var request = new MockHttpServletRequest();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
            var authenticator = new AccountSessionAuthenticator(accountUserDetailsService, passwordEncoder);

            // when
            authenticator.authenticateFederatedAndPersistSession("member@example.com");

            // then
            var persistedContext = request.getSession()
                    .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            assertThat(persistedContext).isEqualTo(SecurityContextHolder.getContext());
        }

        @Test
        void email_removesStaleSavedRequest() {
            // given
            var request = new MockHttpServletRequest("GET", "/oauth2/authorize");
            var response = new MockHttpServletResponse();
            var requestCache = new HttpSessionRequestCache();
            requestCache.saveRequest(request, response);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
            var authenticator = new AccountSessionAuthenticator(accountUserDetailsService, passwordEncoder);

            // when
            authenticator.authenticateFederatedAndPersistSession("member@example.com");

            // then
            assertThat(requestCache.getRequest(request, response)).isNull();
        }

        @Test
        void noHttpServletResponseAvailable_throwsIllegalStateException() {
            // given
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            var authenticator = new AccountSessionAuthenticator(accountUserDetailsService, passwordEncoder);

            // when / then
            assertThatIllegalStateException()
                    .isThrownBy(() -> authenticator.authenticateFederatedAndPersistSession("member@example.com"))
                    .withMessage("No HttpServletResponse available for the current request");
        }
    }

    private record SessionAuthentication(String name, boolean authenticated, List<String> factorAuthorities) {
    }
}
