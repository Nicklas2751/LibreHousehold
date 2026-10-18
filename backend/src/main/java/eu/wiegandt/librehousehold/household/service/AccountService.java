package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.exception.EmailNotVerifiedException;
import eu.wiegandt.librehousehold.household.exception.InvalidPasswordException;
import eu.wiegandt.librehousehold.household.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.model.AccountIdentityEntity;
import eu.wiegandt.librehousehold.household.repository.AccountIdentityRepository;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountIdentityRepository accountIdentityRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, AccountIdentityRepository accountIdentityRepository,
                          PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.accountIdentityRepository = accountIdentityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void createAccount(UUID memberId, String rawPassword) {
        accountRepository.save(new AccountEntity(
                memberId, passwordEncoder.encode(rawPassword), false, Instant.now(), null, 0, null));
    }

    public void resetPassword(UUID memberId, String newPassword) {
        accountRepository.updatePasswordHash(memberId, passwordEncoder.encode(newPassword));
    }

    public void changePassword(UUID memberId, String oldPassword, String newPassword) {
        var account = accountRepository.findById(memberId).orElseThrow(MemberNotFoundException::new);
        if (!account.emailVerified()) {
            throw new EmailNotVerifiedException();
        }
        if (!passwordEncoder.matches(oldPassword, account.passwordHash())) {
            throw new InvalidPasswordException();
        }
        accountRepository.updatePasswordHash(memberId, passwordEncoder.encode(newPassword));
    }

    public boolean hasPassword(UUID memberId) {
        return accountRepository.findById(memberId).map(AccountEntity::passwordHash).isPresent();
    }

    public boolean isEmailVerified(UUID memberId) {
        return accountRepository.findById(memberId).map(AccountEntity::emailVerified).orElse(false);
    }

    public void markEmailVerified(UUID memberId) {
        accountRepository.markEmailVerified(memberId);
    }

    /**
     * Creates an account without a local password: its owner signs in via a social-login provider
     * only. Verified right away, because the provider already confirmed the e-mail address.
     */
    public void createFederatedAccount(UUID memberId) {
        accountRepository.save(new AccountEntity(memberId, null, true, Instant.now(), null, 0, null));
    }

    public void linkExternalIdentity(UUID memberId, String providerId, String externalSubject) {
        accountIdentityRepository.save(new AccountIdentityEntity(null, memberId, providerId, externalSubject, Instant.now()));
    }

    public Optional<UUID> findMemberIdByExternalIdentity(String providerId, String externalSubject) {
        return accountIdentityRepository.findByProviderIdAndExternalSubject(providerId, externalSubject)
                .map(AccountIdentityEntity::memberId);
    }
}
