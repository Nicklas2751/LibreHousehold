package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import eu.wiegandt.librehousehold.core.AccountRegistration;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class AccountRegistrationImpl implements AccountRegistration {

    private final AccountRepository accountRepository;

    AccountRegistrationImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void registerLocalAccount(UUID accountId, String email, String encodedPassword) {
        accountRepository.save(new AccountEntity(accountId, email, encodedPassword));
    }
}
