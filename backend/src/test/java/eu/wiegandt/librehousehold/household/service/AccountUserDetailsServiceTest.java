package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountLockedException;
import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
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
    private MemberRepository memberRepository;

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
            doReturn(Optional.empty()).when(memberRepository).findByEmail(email);

            // when / then
            assertThatThrownBy(() -> accountUserDetailsService.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        void memberExistsWithoutAccount_throwsUsernameNotFoundException() {
            // given
            var email = "max@example.com";
            var memberId = UUID.randomUUID();
            doReturn(Optional.of(member(memberId, email))).when(memberRepository).findByEmail(email);
            doReturn(Optional.empty()).when(accountRepository).findById(memberId);

            // when / then
            assertThatThrownBy(() -> accountUserDetailsService.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        void unverifiedAccount_returnsPrincipalWithEnabledFalse() {
            // given
            var email = "max@example.com";
            var member = member(UUID.randomUUID(), email);
            var passwordHash = "$argon2id$...";
            var expectedPrincipal = new AccountPrincipal(member.id(), member.householdId(), passwordHash, false);
            doReturn(Optional.of(member)).when(memberRepository).findByEmail(email);
            doReturn(Optional.of(new AccountEntity(member.id(), passwordHash, false, Instant.now(), null, 0, null)))
                    .when(accountRepository).findById(member.id());

            // when
            var result = accountUserDetailsService.loadUserByUsername(email);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedPrincipal);
        }

        @Test
        void verifiedAccount_returnsPrincipalWithEnabledTrue() {
            // given
            var email = "max@example.com";
            var member = member(UUID.randomUUID(), email);
            var passwordHash = "$argon2id$...";
            var expectedPrincipal = new AccountPrincipal(member.id(), member.householdId(), passwordHash, true);
            doReturn(Optional.of(member)).when(memberRepository).findByEmail(email);
            doReturn(Optional.of(new AccountEntity(member.id(), passwordHash, true, Instant.now(), null, 0, null)))
                    .when(accountRepository).findById(member.id());

            // when
            var result = accountUserDetailsService.loadUserByUsername(email);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedPrincipal);
        }

        @Test
        void lockedAccount_throwsAccountLocked() {
            // given
            var email = "max@example.com";
            var memberId = UUID.randomUUID();
            var lockedUntil = Instant.now().plusSeconds(600);
            doReturn(Optional.of(member(memberId, email))).when(memberRepository).findByEmail(email);
            doReturn(Optional.of(new AccountEntity(memberId, "$argon2id$...", true, Instant.now(), null, 10, lockedUntil)))
                    .when(accountRepository).findById(memberId);

            // when / then
            assertThatThrownBy(() -> accountUserDetailsService.loadUserByUsername(email))
                    .isInstanceOfSatisfying(AccountLockedException.class,
                            ex -> assertThat(ex.getLockedUntil()).isEqualTo(lockedUntil));
        }

        @Test
        void federatedAccount_throwsUsernameNotFound() {
            // given
            var email = "max@example.com";
            var memberId = UUID.randomUUID();
            doReturn(Optional.of(member(memberId, email))).when(memberRepository).findByEmail(email);
            doReturn(Optional.of(new AccountEntity(memberId, null, true, Instant.now(), null, 0, null)))
                    .when(accountRepository).findById(memberId);

            // when / then
            assertThatThrownBy(() -> accountUserDetailsService.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        void lockedFederatedAccount_throwsUsernameNotFound() {
            // given
            var email = "max@example.com";
            var memberId = UUID.randomUUID();
            var lockedUntil = Instant.now().plusSeconds(600);
            doReturn(Optional.of(member(memberId, email))).when(memberRepository).findByEmail(email);
            doReturn(Optional.of(new AccountEntity(memberId, null, true, Instant.now(), null, 10, lockedUntil)))
                    .when(accountRepository).findById(memberId);

            // when / then
            assertThatThrownBy(() -> accountUserDetailsService.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        void lockedUntilInPast_returnsPrincipal() {
            // given
            var email = "max@example.com";
            var member = member(UUID.randomUUID(), email);
            var passwordHash = "$argon2id$...";
            var lockedUntil = Instant.now().minusSeconds(600);
            var expectedPrincipal = new AccountPrincipal(member.id(), member.householdId(), passwordHash, true);
            doReturn(Optional.of(member)).when(memberRepository).findByEmail(email);
            doReturn(Optional.of(new AccountEntity(member.id(), passwordHash, true, Instant.now(), null, 10, lockedUntil)))
                    .when(accountRepository).findById(member.id());

            // when
            var result = accountUserDetailsService.loadUserByUsername(email);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedPrincipal);
        }
    }

    @Nested
    class loadUserByMemberId {

        @Test
        void unverified_returnsPrincipal() {
            // given
            var member = member(UUID.randomUUID(), "max@example.com");
            var passwordHash = "$argon2id$...";
            var lockedUntil = Instant.now().plusSeconds(600);
            var expectedPrincipal = new AccountPrincipal(member.id(), member.householdId(), passwordHash, false);
            doReturn(Optional.of(member)).when(memberRepository).findById(member.id());
            doReturn(Optional.of(new AccountEntity(member.id(), passwordHash, false, Instant.now(), null, 10, lockedUntil)))
                    .when(accountRepository).findById(member.id());

            // when
            var result = accountUserDetailsService.loadUserByMemberId(member.id());

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expectedPrincipal);
        }

        @Test
        void unknownMember_throwsUsernameNotFound() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(Optional.empty()).when(memberRepository).findById(memberId);

            // when / then
            assertThatThrownBy(() -> accountUserDetailsService.loadUserByMemberId(memberId))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        void memberWithoutAccount_throwsUsernameNotFound() {
            // given
            var member = member(UUID.randomUUID(), "max@example.com");
            doReturn(Optional.of(member)).when(memberRepository).findById(member.id());
            doReturn(Optional.empty()).when(accountRepository).findById(member.id());

            // when / then
            assertThatThrownBy(() -> accountUserDetailsService.loadUserByMemberId(member.id()))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
    }

    private MemberEntity member(UUID memberId, String email) {
        return new MemberEntity(memberId, "Max", email, null, UUID.randomUUID(), false);
    }
}
