package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.VerificationDeletionWarningRequested;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, InstancioExtension.class, OutputCaptureExtension.class})
class UnverifiedAccountExpiryJobTest {

    private static final int GRACE_PERIOD_DAYS = 7;
    private static final int DELETION_WARNING_HOURS_BEFORE = 24;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private HouseholdManagementService householdManagementService;

    @Mock
    private MemberManagementService memberManagementService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Nested
    class run {

        @Test
        void accountPastWarningThresholdNotYetWarned_sendsWarningEmailAndSetsTimestamp() {
            // given
            var job = buildJob();
            var account = new AccountEntity(UUID.randomUUID(), "hash", false, Instant.now(), null, 0, null);
            var member = Instancio.of(MemberEntity.class).set(field(MemberEntity::id), account.memberId()).create();
            doReturn(List.of(account)).when(accountRepository)
                    .findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(any());
            doReturn(List.of()).when(accountRepository).findByEmailVerifiedFalseAndRegisteredAtBefore(any());
            doReturn(Optional.of(member)).when(memberRepository).findById(account.memberId());

            // when
            job.run();

            // then
            verify(eventPublisher).publishEvent(new VerificationDeletionWarningRequested(member.getId(), member.email()));
        }

        @Test
        void accountPastWarningThreshold_setsWarningTimestampOnAccount() {
            // given
            var job = buildJob();
            var account = new AccountEntity(UUID.randomUUID(), "hash", false, Instant.now(), null, 0, null);
            var member = Instancio.of(MemberEntity.class).set(field(MemberEntity::id), account.memberId()).create();
            var beforeRun = Instant.now();
            doReturn(List.of(account)).when(accountRepository)
                    .findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(any());
            doReturn(List.of()).when(accountRepository).findByEmailVerifiedFalseAndRegisteredAtBefore(any());
            doReturn(Optional.of(member)).when(memberRepository).findById(account.memberId());

            // when
            job.run();

            // then
            verify(accountRepository).updateVerificationDeletionWarningSentAt(
                    eq(account.memberId()), argThat(sentAt -> !sentAt.isBefore(beforeRun)));
        }

        @Test
        void twoAccountsPastWarningThreshold_commitsOneTransactionPerAccount() {
            // given
            var job = buildJob();
            var firstAccount = new AccountEntity(UUID.randomUUID(), "hash", false, Instant.now(), null, 0, null);
            var secondAccount = new AccountEntity(UUID.randomUUID(), "hash", false, Instant.now(), null, 0, null);
            doReturn(List.of(firstAccount, secondAccount)).when(accountRepository)
                    .findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(any());
            doReturn(List.of()).when(accountRepository).findByEmailVerifiedFalseAndRegisteredAtBefore(any());
            doReturn(Optional.of(Instancio.of(MemberEntity.class).set(field(MemberEntity::id), firstAccount.memberId()).create()))
                    .when(memberRepository).findById(firstAccount.memberId());
            doReturn(Optional.of(Instancio.of(MemberEntity.class).set(field(MemberEntity::id), secondAccount.memberId()).create()))
                    .when(memberRepository).findById(secondAccount.memberId());

            // when
            job.run();

            // then
            verify(transactionManager, times(2)).commit(any());
        }

        @Test
        void noAccountsPastWarningThreshold_doesNotSendWarningEmail() {
            // given
            var job = buildJob();
            doReturn(List.of()).when(accountRepository)
                    .findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(any());
            doReturn(List.of()).when(accountRepository).findByEmailVerifiedFalseAndRegisteredAtBefore(any());

            // when
            job.run();

            // then
            verify(eventPublisher, never()).publishEvent(any(VerificationDeletionWarningRequested.class));
        }

        @Test
        void adminAccountPastGracePeriod_deletesEntireHousehold() {
            // given
            var job = buildJob();
            var householdId = UUID.randomUUID();
            var account = new AccountEntity(UUID.randomUUID(), "hash", false, Instant.now(), null, 0, null);
            var member = Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::id), account.memberId())
                    .set(field(MemberEntity::householdId), householdId)
                    .set(field(MemberEntity::isAdmin), true)
                    .create();
            doReturn(List.of()).when(accountRepository)
                    .findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(any());
            doReturn(List.of(account)).when(accountRepository).findByEmailVerifiedFalseAndRegisteredAtBefore(any());
            doReturn(Optional.of(member)).when(memberRepository).findById(account.memberId());

            // when
            job.run();

            // then
            verify(householdManagementService).deleteHousehold(householdId);
        }

        @Test
        void nonAdminAccountPastGracePeriod_removesOnlyThatMember() {
            // given
            var job = buildJob();
            var householdId = UUID.randomUUID();
            var account = new AccountEntity(UUID.randomUUID(), "hash", false, Instant.now(), null, 0, null);
            var member = Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::id), account.memberId())
                    .set(field(MemberEntity::householdId), householdId)
                    .set(field(MemberEntity::isAdmin), false)
                    .create();
            doReturn(List.of()).when(accountRepository)
                    .findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(any());
            doReturn(List.of(account)).when(accountRepository).findByEmailVerifiedFalseAndRegisteredAtBefore(any());
            doReturn(Optional.of(member)).when(memberRepository).findById(account.memberId());

            // when
            job.run();

            // then
            verify(memberManagementService).removeMember(householdId, member.getId());
        }

        @Test
        void firstWarningFails_stillWarnsSecondAccount() {
            // given
            var job = buildJob();
            var failingAccount = new AccountEntity(UUID.randomUUID(), "hash", false, Instant.now(), null, 0, null);
            var secondAccount = new AccountEntity(UUID.randomUUID(), "hash", false, Instant.now(), null, 0, null);
            var secondMember = Instancio.of(MemberEntity.class).set(field(MemberEntity::id), secondAccount.memberId()).create();
            doReturn(List.of(failingAccount, secondAccount)).when(accountRepository)
                    .findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(any());
            doReturn(List.of()).when(accountRepository).findByEmailVerifiedFalseAndRegisteredAtBefore(any());
            doReturn(Optional.empty()).when(memberRepository).findById(failingAccount.memberId());
            doReturn(Optional.of(secondMember)).when(memberRepository).findById(secondAccount.memberId());

            // when
            job.run();

            // then
            verify(eventPublisher).publishEvent(new VerificationDeletionWarningRequested(secondMember.getId(), secondMember.email()));
        }

        @Test
        void firstDeletionFails_stillDeletesSecondAccount() {
            // given
            var job = buildJob();
            var failingAccount = new AccountEntity(UUID.randomUUID(), "hash", false, Instant.now(), null, 0, null);
            var secondAccount = new AccountEntity(UUID.randomUUID(), "hash", false, Instant.now(), null, 0, null);
            var failingMember = Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::id), failingAccount.memberId())
                    .set(field(MemberEntity::isAdmin), false)
                    .create();
            var secondMember = Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::id), secondAccount.memberId())
                    .set(field(MemberEntity::isAdmin), false)
                    .create();
            doReturn(List.of()).when(accountRepository)
                    .findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(any());
            doReturn(List.of(failingAccount, secondAccount)).when(accountRepository).findByEmailVerifiedFalseAndRegisteredAtBefore(any());
            doReturn(Optional.of(failingMember)).when(memberRepository).findById(failingAccount.memberId());
            doReturn(Optional.of(secondMember)).when(memberRepository).findById(secondAccount.memberId());
            doThrow(new IllegalStateException("simulated failure")).when(memberManagementService)
                    .removeMember(failingMember.householdId(), failingMember.getId());

            // when
            job.run();

            // then
            verify(memberManagementService).removeMember(secondMember.householdId(), secondMember.getId());
        }

        @Test
        void deletionFails_logsErrorWithMemberIdButWithoutEmail(CapturedOutput output) {
            // given
            var job = buildJob();
            var account = new AccountEntity(UUID.randomUUID(), "hash", false, Instant.now(), null, 0, null);
            var member = Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::id), account.memberId())
                    .set(field(MemberEntity::isAdmin), false)
                    .create();
            doReturn(List.of()).when(accountRepository)
                    .findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(any());
            doReturn(List.of(account)).when(accountRepository).findByEmailVerifiedFalseAndRegisteredAtBefore(any());
            doReturn(Optional.of(member)).when(memberRepository).findById(account.memberId());
            doThrow(new IllegalStateException("simulated failure")).when(memberManagementService)
                    .removeMember(member.householdId(), member.getId());

            // when
            job.run();

            // then
            assertThat(output.getAll())
                    .contains("ERROR")
                    .contains(member.getId().toString())
                    .doesNotContain(member.email());
        }

        @Test
        void noAccountsPastGracePeriod_doesNotDeleteAnything() {
            // given
            var job = buildJob();
            doReturn(List.of()).when(accountRepository)
                    .findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(any());
            doReturn(List.of()).when(accountRepository).findByEmailVerifiedFalseAndRegisteredAtBefore(any());

            // when
            job.run();

            // then
            verify(householdManagementService, never()).deleteHousehold(any());
        }
    }

    private UnverifiedAccountExpiryJob buildJob() {
        return new UnverifiedAccountExpiryJob(accountRepository, memberRepository, householdManagementService,
                memberManagementService, eventPublisher, new TransactionTemplate(transactionManager), GRACE_PERIOD_DAYS, DELETION_WARNING_HOURS_BEFORE);
    }
}
