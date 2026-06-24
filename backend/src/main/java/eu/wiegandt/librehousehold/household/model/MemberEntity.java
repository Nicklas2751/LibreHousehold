package eu.wiegandt.librehousehold.household.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("member")
public record MemberEntity(
        @Id UUID id,
        String name,
        String avatar,
        @Column("household_id") UUID householdId,
        @Column("is_admin") boolean isAdmin
) implements Persistable<UUID> {

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
