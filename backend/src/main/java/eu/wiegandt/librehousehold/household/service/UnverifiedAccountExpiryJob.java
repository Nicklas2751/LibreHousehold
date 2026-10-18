package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.VerificationDeletionWarningRequested;
import eu.wiegandt.librehousehold.household.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;

/**
 * Periodically warns about, then deletes, accounts that never verified their email address (see
 * VERIFY1/ENUM1 in Arc42 Chapter 8. Runs hourly: the interval itself is not
 * configurable, since there is no need to tune it at runtime.
 *
 * <p>Publishes {@link VerificationDeletionWarningRequested} rather than calling the
 * {@code notifications} module's {@code EmailSenderService} directly: {@code household} must not
 * depend on {@code notifications} (see ADR-011) — a direct call would also create a Spring Modulith
 * dependency cycle, since {@code notifications} already depends on {@code household}'s public API
 * (e.g. {@code AccountTokenIssuer}).
 */
@Component
public class UnverifiedAccountExpiryJob {

    private static final Logger LOG = LoggerFactory.getLogger(UnverifiedAccountExpiryJob.class);
    private static final long RUN_INTERVAL_MILLIS = 3_600_000;

    private final AccountRepository accountRepository;
    private final MemberRepository memberRepository;
    private final HouseholdManagementService householdManagementService;
    private final MemberManagementService memberManagementService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactionOperations;
    private final int gracePeriodDays;
    private final int deletionWarningHoursBefore;

    public UnverifiedAccountExpiryJob(
            AccountRepository accountRepository,
            MemberRepository memberRepository,
            HouseholdManagementService householdManagementService,
            MemberManagementService memberManagementService,
            ApplicationEventPublisher eventPublisher,
            TransactionOperations transactionOperations,
            @Value("${librehousehold.security.email-verification.grace-period-days}") int gracePeriodDays,
            @Value("${librehousehold.security.email-verification.deletion-warning-hours-before}") int deletionWarningHoursBefore) {
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
        this.householdManagementService = householdManagementService;
        this.memberManagementService = memberManagementService;
        this.eventPublisher = eventPublisher;
        this.transactionOperations = transactionOperations;
        this.gracePeriodDays = gracePeriodDays;
        this.deletionWarningHoursBefore = deletionWarningHoursBefore;
    }

    @Scheduled(fixedRate = RUN_INTERVAL_MILLIS)
    public void run() {
        sendDeletionWarnings();
        deleteExpiredAccounts();
    }

    private void sendDeletionWarnings() {
        var warningThreshold = Instant.now()
                .minus(Duration.ofDays(gracePeriodDays))
                .plus(Duration.ofHours(deletionWarningHoursBefore));
        accountRepository
                .findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(warningThreshold)
                .forEach(this::sendDeletionWarningIsolated);
    }

    // One broken account must not keep all others from being warned in this run.
    private void sendDeletionWarningIsolated(AccountEntity account) {
        try {
            transactionOperations.executeWithoutResult(_ -> sendDeletionWarning(account));
        } catch (RuntimeException exception) {
            logFailure("send deletion warning", account, exception);
        }
    }

    private void sendDeletionWarning(AccountEntity account) {
        var member = findMember(account);
        eventPublisher.publishEvent(new VerificationDeletionWarningRequested(member.getId(), member.email()));
        accountRepository.updateVerificationDeletionWarningSentAt(account.memberId(), Instant.now());
    }

    private void deleteExpiredAccounts() {
        var deletionThreshold = Instant.now().minus(Duration.ofDays(gracePeriodDays));
        accountRepository.findByEmailVerifiedFalseAndRegisteredAtBefore(deletionThreshold)
                .forEach(this::deleteExpiredAccountIsolated);
    }

    // One broken account must not keep all others from being deleted in this run.
    private void deleteExpiredAccountIsolated(AccountEntity account) {
        try {
            deleteExpiredAccount(account);
        } catch (RuntimeException exception) {
            logFailure("delete expired account", account, exception);
        }
    }

    private void deleteExpiredAccount(AccountEntity account) {
        var member = findMember(account);
        if (member.isAdmin()) {
            householdManagementService.deleteHousehold(member.householdId());
        } else {
            memberManagementService.removeMember(member.householdId(), member.getId());
        }
    }

    // Logs the member ID only: the e-mail address is personal data and must not end up in logs.
    private void logFailure(String action, AccountEntity account, RuntimeException exception) {
        LOG.error("Failed to {} for member {}", action, account.memberId(), exception);
    }

    private MemberEntity findMember(AccountEntity account) {
        return memberRepository.findById(account.memberId()).orElseThrow(MemberNotFoundException::new);
    }
}
