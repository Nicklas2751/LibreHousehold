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
}
