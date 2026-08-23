package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.exception.InvalidPasswordException;
import eu.wiegandt.librehousehold.household.exception.MemberNotFoundException;
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

    public void changePassword(UUID memberId, String oldPassword, String newPassword) {
        var account = accountRepository.findById(memberId).orElseThrow(MemberNotFoundException::new);
        if (!passwordEncoder.matches(oldPassword, account.passwordHash())) {
            throw new InvalidPasswordException();
        }
        accountRepository.updatePasswordHash(memberId, passwordEncoder.encode(newPassword));
    }
}
