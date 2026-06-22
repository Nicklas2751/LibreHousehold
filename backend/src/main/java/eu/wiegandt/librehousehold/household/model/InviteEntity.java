package eu.wiegandt.librehousehold.household.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.UUID;

@Table("invite")
public record InviteEntity(
        @Id Long id,
        @Column("household_id") UUID householdId,
        UUID token,
        @Column("valid_until") LocalDate validUntil
) {
}
