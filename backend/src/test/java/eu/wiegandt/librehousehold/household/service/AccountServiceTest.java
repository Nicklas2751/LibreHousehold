package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    @Test
    void createAccount_rawPassword_storesArgon2idHash() {
        // given
        var memberId = UUID.randomUUID();
        var rawPassword = "correct horse battery staple";
        var hashedPassword = "$argon2id$v=19$m=19456,t=2,p=1$...";
        var expectedAccount = new AccountEntity(memberId, hashedPassword);
        doReturn(hashedPassword).when(passwordEncoder).encode(rawPassword);

        // when
        accountService.createAccount(memberId, rawPassword);

        // then
        var captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(expectedAccount);
    }
}
