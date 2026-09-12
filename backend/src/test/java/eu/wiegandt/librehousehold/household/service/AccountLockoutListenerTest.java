package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountLockoutListenerTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    @Mock
    private MemberManagementService memberManagementService;

    @Mock
    private AccountRepository accountRepository;

    private AccountLockoutListener newListener() {
        return new AccountLockoutListener(memberManagementService, accountRepository,
                new LockoutProperties(MAX_ATTEMPTS, LOCKOUT_DURATION));
    }

    @Nested
    class onAuthenticationFailure {

        @Test
        void thresholdReached_locksAccountForConfiguredDuration() {
            // given
            var email = "max@example.com";
            var memberId = UUID.randomUUID();
            var event = new AuthenticationFailureBadCredentialsEvent(
                    new TestingAuthenticationToken(email, "wrong-password"), new BadCredentialsException("bad credentials"));
            doReturn(Optional.of(memberId)).when(memberManagementService).findMemberIdByEmail(email);
            doReturn(Optional.of(new AccountEntity(memberId, "hash", true, Instant.now(), null, MAX_ATTEMPTS, null)))
                    .when(accountRepository).findById(memberId);

            // when
            newListener().onAuthenticationFailure(event);

            // then
            var lockedUntilCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(accountRepository).incrementFailedLoginAttempts(memberId);
            verify(accountRepository).lockUntil(eq(memberId), lockedUntilCaptor.capture());
            assertThat(lockedUntilCaptor.getValue()).isCloseTo(Instant.now().plus(LOCKOUT_DURATION), within(2, ChronoUnit.SECONDS));
        }

        @Test
        void belowThreshold_doesNotLockAccount() {
            // given
            var email = "max@example.com";
            var memberId = UUID.randomUUID();
            var event = new AuthenticationFailureBadCredentialsEvent(
                    new TestingAuthenticationToken(email, "wrong-password"), new BadCredentialsException("bad credentials"));
            doReturn(Optional.of(memberId)).when(memberManagementService).findMemberIdByEmail(email);
            doReturn(Optional.of(new AccountEntity(memberId, "hash", true, Instant.now(), null, MAX_ATTEMPTS - 2, null)))
                    .when(accountRepository).findById(memberId);

            // when
            newListener().onAuthenticationFailure(event);

            // then
            verify(accountRepository).incrementFailedLoginAttempts(memberId);
            verify(accountRepository, never()).lockUntil(eq(memberId), any());
        }
    }

    @Nested
    class onAuthenticationSuccess {

        @Test
        void successfulLogin_resetsFailedAttemptCounter() {
            // given
            var email = "max@example.com";
            var memberId = UUID.randomUUID();
            var event = new AuthenticationSuccessEvent(new TestingAuthenticationToken(email, "correct-password"));
            doReturn(Optional.of(memberId)).when(memberManagementService).findMemberIdByEmail(email);

            // when
            newListener().onAuthenticationSuccess(event);

            // then
            verify(accountRepository).resetFailedLoginAttempts(memberId);
        }
    }
}
