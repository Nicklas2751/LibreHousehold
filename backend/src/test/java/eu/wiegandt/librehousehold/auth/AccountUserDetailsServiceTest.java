package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class AccountUserDetailsServiceTest {

    @InjectMocks
    AccountUserDetailsService service;

    @Mock
    AccountRepository accountRepository;

    @Nested
    class loadUserByUsername {

        @Test
        void knownEmail_returnsUserDetailsWithAccountIdAsUsername() {
            // given
            var accountId = UUID.randomUUID();
            var email = "user@example.com";
            var entity = new AccountEntity(accountId, email, "hashed");
            doReturn(Optional.of(entity)).when(accountRepository).findByEmail(email);

            // when
            var result = service.loadUserByUsername(email);

            // then
            assertThat(result.getUsername()).isEqualTo(accountId.toString());
        }

        @Test
        void unknownEmail_throwsUsernameNotFoundException() {
            // given
            var email = "unknown@example.com";
            doReturn(Optional.empty()).when(accountRepository).findByEmail(email);

            // when / then
            assertThatThrownBy(() -> service.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
    }
}
