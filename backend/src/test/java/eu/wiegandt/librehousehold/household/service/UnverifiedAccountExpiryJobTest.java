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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
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
                memberManagementService, eventPublisher, GRACE_PERIOD_DAYS, DELETION_WARNING_HOURS_BEFORE);
    }
}
