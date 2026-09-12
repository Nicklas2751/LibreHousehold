package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.exception.EmailNotVerifiedException;
import eu.wiegandt.librehousehold.household.exception.InvalidPasswordException;
import eu.wiegandt.librehousehold.household.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    @Nested
    class createAccount {

        @Test
        void rawPassword_storesArgon2idHash() {
            // given
            var memberId = UUID.randomUUID();
            var rawPassword = "correct horse battery staple";
            var hashedPassword = "$argon2id$v=19$m=19456,t=2,p=1$...";
            doReturn(hashedPassword).when(passwordEncoder).encode(rawPassword);

            // when
            accountService.createAccount(memberId, rawPassword);

            // then
            var captor = ArgumentCaptor.forClass(AccountEntity.class);
            verify(accountRepository).save(captor.capture());
            assertThat(captor.getValue().passwordHash()).isEqualTo(hashedPassword);
        }

        @Test
        void rawPassword_storesAccountAsUnverifiedWithRegisteredAtNow() {
            // given
            var memberId = UUID.randomUUID();
            var rawPassword = "correct horse battery staple";
            var beforeCreation = Instant.now();

            // when
            accountService.createAccount(memberId, rawPassword);
            var afterCreation = Instant.now();

            // then
            var captor = ArgumentCaptor.forClass(AccountEntity.class);
            verify(accountRepository).save(captor.capture());
            var expectedSavedAccount = new AccountEntity(memberId, null, false, beforeCreation, null, 0, null);
            Comparator<Instant> registeredAtWithinCreationWindow = (actual, expected) -> {
                if (actual == null || expected == null) {
                    return Objects.equals(actual, expected) ? 0 : -1;
                }
                return !actual.isBefore(beforeCreation) && !actual.isAfter(afterCreation) ? 0 : -1;
            };
            assertThat(captor.getValue()).usingRecursiveComparison()
                    .withComparatorForType(registeredAtWithinCreationWindow, Instant.class)
                    .isEqualTo(expectedSavedAccount);
        }
    }

    @Nested
    class changePassword {

        @Test
        void accountNotFound_throwsMemberNotFoundException() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(Optional.empty()).when(accountRepository).findById(memberId);

            // when / then
            assertThatThrownBy(() -> accountService.changePassword(memberId, "oldPassword", "newPassword"))
                    .isInstanceOf(MemberNotFoundException.class);
        }

        @Test
        void unverifiedAccount_throwsEmailNotVerifiedExceptionWithoutUpdating() {
            // given
            var memberId = UUID.randomUUID();
            var account = new AccountEntity(memberId, "$argon2id$v=19$m=19456,t=2,p=1$...", false, Instant.now(), null, 0, null);
            doReturn(Optional.of(account)).when(accountRepository).findById(memberId);

            // when / then
            assertThatThrownBy(() -> accountService.changePassword(memberId, "oldPassword", "newPassword"))
                    .isInstanceOf(EmailNotVerifiedException.class);
            verify(accountRepository, never()).updatePasswordHash(any(), any());
        }

        @Test
        void verifiedAccountWrongOldPassword_throwsInvalidPasswordExceptionWithoutUpdating() {
            // given
            var memberId = UUID.randomUUID();
            var account = new AccountEntity(memberId, "$argon2id$v=19$m=19456,t=2,p=1$...", true, Instant.now(), null, 0, null);
            doReturn(Optional.of(account)).when(accountRepository).findById(memberId);
            doReturn(false).when(passwordEncoder).matches("wrongOldPassword", account.passwordHash());

            // when / then
            assertThatThrownBy(() -> accountService.changePassword(memberId, "wrongOldPassword", "newPassword"))
                    .isInstanceOf(InvalidPasswordException.class);
            verify(accountRepository, never()).updatePasswordHash(any(), any());
        }

        @Test
        void verifiedAccountCorrectOldPassword_updatesPasswordHash() {
            // given
            var memberId = UUID.randomUUID();
            var account = new AccountEntity(memberId, "$argon2id$v=19$m=19456,t=2,p=1$...", true, Instant.now(), null, 0, null);
            var newHashedPassword = "$argon2id$v=19$m=19456,t=2,p=1$newHash";
            doReturn(Optional.of(account)).when(accountRepository).findById(memberId);
            doReturn(true).when(passwordEncoder).matches("correctOldPassword", account.passwordHash());
            doReturn(newHashedPassword).when(passwordEncoder).encode("newPassword");

            // when
            accountService.changePassword(memberId, "correctOldPassword", "newPassword");

            // then
            verify(accountRepository).updatePasswordHash(memberId, newHashedPassword);
        }
    }

    @Nested
    class resetPassword {

        @Test
        void newPassword_updatesPasswordHashWithoutCheckingOldPassword() {
            // given
            var memberId = UUID.randomUUID();
            var newHashedPassword = "$argon2id$v=19$m=19456,t=2,p=1$newHash";
            doReturn(newHashedPassword).when(passwordEncoder).encode("newPassword");

            // when
            accountService.resetPassword(memberId, "newPassword");

            // then
            verify(accountRepository).updatePasswordHash(memberId, newHashedPassword);
        }
    }

    @Nested
    class isEmailVerified {

        @Test
        void noAccount_returnsFalse() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(Optional.empty()).when(accountRepository).findById(memberId);

            // when
            var result = accountService.isEmailVerified(memberId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void unverifiedAccount_returnsFalse() {
            // given
            var memberId = UUID.randomUUID();
            var account = new AccountEntity(memberId, "$argon2id$...", false, Instant.now(), null, 0, null);
            doReturn(Optional.of(account)).when(accountRepository).findById(memberId);

            // when
            var result = accountService.isEmailVerified(memberId);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void verifiedAccount_returnsTrue() {
            // given
            var memberId = UUID.randomUUID();
            var account = new AccountEntity(memberId, "$argon2id$...", true, Instant.now(), null, 0, null);
            doReturn(Optional.of(account)).when(accountRepository).findById(memberId);

            // when
            var result = accountService.isEmailVerified(memberId);

            // then
            assertThat(result).isTrue();
        }
    }
}
