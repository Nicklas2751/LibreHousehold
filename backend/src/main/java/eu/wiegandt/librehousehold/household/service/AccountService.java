package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void createAccount(UUID memberId, String rawPassword) {
        accountRepository.save(new AccountEntity(memberId, passwordEncoder.encode(rawPassword)));
    }
}
