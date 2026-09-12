package eu.wiegandt.librehousehold.household.repository;

import eu.wiegandt.librehousehold.household.model.AccountEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AccountRepository extends CrudRepository<AccountEntity, UUID> {

    @Modifying
    @Query("UPDATE account SET password_hash = :passwordHash WHERE member_id = :memberId")
    void updatePasswordHash(@Param("memberId") UUID memberId, @Param("passwordHash") String passwordHash);

    @Modifying
    @Query("UPDATE account SET email_verified = true WHERE member_id = :memberId")
    void markEmailVerified(@Param("memberId") UUID memberId);

    List<AccountEntity> findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(Instant threshold);

    List<AccountEntity> findByEmailVerifiedFalseAndRegisteredAtBefore(Instant threshold);

    @Modifying
    @Query("UPDATE account SET verification_deletion_warning_sent_at = :sentAt WHERE member_id = :memberId")
    void updateVerificationDeletionWarningSentAt(@Param("memberId") UUID memberId, @Param("sentAt") Instant sentAt);

    @Modifying
    @Query("UPDATE account SET failed_login_attempts = failed_login_attempts + 1 WHERE member_id = :memberId")
    void incrementFailedLoginAttempts(@Param("memberId") UUID memberId);

    @Modifying
    @Query("UPDATE account SET locked_until = :lockedUntil WHERE member_id = :memberId")
    void lockUntil(@Param("memberId") UUID memberId, @Param("lockedUntil") Instant lockedUntil);

    @Modifying
    @Query("UPDATE account SET failed_login_attempts = 0, locked_until = NULL WHERE member_id = :memberId")
    void resetFailedLoginAttempts(@Param("memberId") UUID memberId);
}
