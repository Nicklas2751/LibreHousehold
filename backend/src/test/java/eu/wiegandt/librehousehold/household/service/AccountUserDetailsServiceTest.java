package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountLockedException;
import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class AccountUserDetailsServiceTest {

    @Mock
    private MemberManagementService memberManagementService;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountUserDetailsService accountUserDetailsService;

    @Nested
    class loadUserByUsername {

        @Test
        void unknownEmail_throwsUsernameNotFoundException() {
            // given
            var email = "unknown@example.com";
            doReturn(Optional.empty()).when(memberManagementService).findMemberIdByEmail(email);

            // when / then
            assertThatThrownBy(() -> accountUserDetailsService.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        void memberExistsWithoutAccount_throwsUsernameNotFoundException() {
            // given
            var email = "max@example.com";
            var memberId = UUID.randomUUID();
            doReturn(Optional.of(memberId)).when(memberManagementService).findMemberIdByEmail(email);
            doReturn(Optional.empty()).when(accountRepository).findById(memberId);

            // when / then
            assertThatThrownBy(() -> accountUserDetailsService.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        void unverifiedAccount_returnsPrincipalWithEnabledFalse() {
            // given
            var email = "max@example.com";
            var memberId = UUID.randomUUID();
            var passwordHash = "$argon2id$...";
            var expectedPrincipal = new AccountPrincipal(email, passwordHash, false);
            doReturn(Optional.of(memberId)).when(memberManagementService).findMemberIdByEmail(email);
            doReturn(Optional.of(new AccountEntity(memberId, passwordHash, false, Instant.now(), null, 0, null)))
                    .when(accountRepository).findById(memberId);

            // when
            var result = accountUserDetailsService.loadUserByUsername(email);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedPrincipal);
        }

        @Test
        void verifiedAccount_returnsPrincipalWithEnabledTrue() {
            // given
            var email = "max@example.com";
            var memberId = UUID.randomUUID();
            var passwordHash = "$argon2id$...";
            var expectedPrincipal = new AccountPrincipal(email, passwordHash, true);
            doReturn(Optional.of(memberId)).when(memberManagementService).findMemberIdByEmail(email);
            doReturn(Optional.of(new AccountEntity(memberId, passwordHash, true, Instant.now(), null, 0, null)))
                    .when(accountRepository).findById(memberId);

            // when
            var result = accountUserDetailsService.loadUserByUsername(email);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedPrincipal);
        }

        @Test
        void lockedUntilInFuture_throwsAccountLockedExceptionCarryingLockedUntil() {
            // given
            var email = "max@example.com";
            var memberId = UUID.randomUUID();
            var lockedUntil = Instant.now().plusSeconds(600);
            doReturn(Optional.of(memberId)).when(memberManagementService).findMemberIdByEmail(email);
            doReturn(Optional.of(new AccountEntity(memberId, "$argon2id$...", true, Instant.now(), null, 10, lockedUntil)))
                    .when(accountRepository).findById(memberId);

            // when / then
            assertThatThrownBy(() -> accountUserDetailsService.loadUserByUsername(email))
                    .isInstanceOfSatisfying(AccountLockedException.class,
                            ex -> assertThat(ex.getLockedUntil()).isEqualTo(lockedUntil));
        }

        @Test
        void lockedUntilInPast_returnsPrincipal() {
            // given
            var email = "max@example.com";
            var memberId = UUID.randomUUID();
            var passwordHash = "$argon2id$...";
            var lockedUntil = Instant.now().minusSeconds(600);
            var expectedPrincipal = new AccountPrincipal(email, passwordHash, true);
            doReturn(Optional.of(memberId)).when(memberManagementService).findMemberIdByEmail(email);
            doReturn(Optional.of(new AccountEntity(memberId, passwordHash, true, Instant.now(), null, 10, lockedUntil)))
                    .when(accountRepository).findById(memberId);

            // when
            var result = accountUserDetailsService.loadUserByUsername(email);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedPrincipal);
        }
    }
}
