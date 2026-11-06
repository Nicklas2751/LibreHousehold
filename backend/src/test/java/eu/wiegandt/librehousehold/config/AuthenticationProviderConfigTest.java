package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class AuthenticationProviderConfigTest {

    private static final String EMAIL = "max@example.com";
    private static final String PASSWORD_HASH = "$argon2id$...";
    private static final String CORRECT_PASSWORD = "correct horse battery staple";

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Nested
    class authenticationProvider {

        @Test
        void verifiedCorrectPassword_authenticates() {
            // given
            var principal = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), PASSWORD_HASH, true);
            doReturn(principal).when(userDetailsService).loadUserByUsername(EMAIL);
            doReturn(true).when(passwordEncoder).matches(CORRECT_PASSWORD, PASSWORD_HASH);
            var provider = new AuthenticationProviderConfig().authenticationProvider(userDetailsService, passwordEncoder);

            // when
            var result = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, CORRECT_PASSWORD));

            // then
            assertThat(result.getPrincipal()).isEqualTo(principal);
        }

        @Test
        void unverifiedWrongPassword_throwsBadCredentials() {
            // given
            var wrongPassword = "wrong password";
            var principal = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), PASSWORD_HASH, false);
            doReturn(principal).when(userDetailsService).loadUserByUsername(EMAIL);
            doReturn(false).when(passwordEncoder).matches(wrongPassword, PASSWORD_HASH);
            var provider = new AuthenticationProviderConfig().authenticationProvider(userDetailsService, passwordEncoder);

            // when / then
            assertThatThrownBy(() -> provider.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, wrongPassword)))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        void unverifiedCorrectPassword_throwsDisabled() {
            // given
            var principal = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), PASSWORD_HASH, false);
            doReturn(principal).when(userDetailsService).loadUserByUsername(EMAIL);
            doReturn(true).when(passwordEncoder).matches(CORRECT_PASSWORD, PASSWORD_HASH);
            var provider = new AuthenticationProviderConfig().authenticationProvider(userDetailsService, passwordEncoder);

            // when / then
            assertThatThrownBy(() -> provider.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, CORRECT_PASSWORD)))
                    .isInstanceOf(DisabledException.class);
        }
    }
}
