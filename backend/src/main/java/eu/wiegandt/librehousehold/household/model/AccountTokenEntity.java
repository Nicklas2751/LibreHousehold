package eu.wiegandt.librehousehold.household.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("account_token")
public record AccountTokenEntity(
        @Id Long id,
        @Column("member_id") UUID memberId,
        UUID token,
        String purpose,
        @Column("valid_until") Instant validUntil
) {
}
