package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.MemberEmailChanged;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class AccountEmailListenerIT {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void on_existingAccount_updatesEmailInDatabase() {
        // given
        var accountId = UUID.randomUUID();
        accountRepository.save(new AccountEntity(accountId, "before@example.com", null));
        var listener = new AccountEmailListener(accountRepository);
        var event = new MemberEmailChanged(accountId, "after@example.com");

        // when
        listener.on(event);

        // then
        assertThat(accountRepository.findByEmail("after@example.com")).isPresent();
    }
}