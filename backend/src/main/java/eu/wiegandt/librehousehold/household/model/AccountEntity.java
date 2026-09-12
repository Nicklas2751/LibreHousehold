package eu.wiegandt.librehousehold.household.model;

import org.jspecify.annotations.NonNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("account")
public record AccountEntity(
        @Id @Column("member_id") UUID memberId,
        @Column("password_hash") String passwordHash,
        @Column("email_verified") boolean emailVerified,
        @Column("registered_at") Instant registeredAt,
        @Column("verification_deletion_warning_sent_at") Instant verificationDeletionWarningSentAt,
        @Column("failed_login_attempts") int failedLoginAttempts,
        @Column("locked_until") Instant lockedUntil
) implements Persistable<UUID> {

    @Override
    public UUID getId() {
        return memberId;
    }

    @Override
    public boolean isNew() {
        return true;
    }

    @Override
    public @NonNull String toString() {
        return "AccountEntity[memberId=" + memberId + "]";
    }
}
