package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountRegistrationImplTest {

    @InjectMocks
    AccountRegistrationImpl accountRegistrationPort;

    @Mock
    AccountRepository accountRepository;

    @Nested
    class registerLocalAccount {

        @Test
        void validInput_savesAccountWithGivenEncodedPasswordAndId() {
            // given
            var accountId = UUID.randomUUID();
            var encodedPassword = "encoded-hash";
            var mail = "max@example.com";

            var expected = new AccountEntity(accountId, mail, encodedPassword);

            // when
            accountRegistrationPort.registerLocalAccount(accountId, mail, encodedPassword);

            // then
            var accountCaptor = ArgumentCaptor.forClass(AccountEntity.class);
            verify(accountRepository).save(accountCaptor.capture());

            assertThat(accountCaptor.getValue()).usingRecursiveComparison().isEqualTo(expected);
        }
    }
}
