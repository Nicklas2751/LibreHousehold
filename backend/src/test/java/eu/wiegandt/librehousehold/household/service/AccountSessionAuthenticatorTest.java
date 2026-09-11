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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
}
