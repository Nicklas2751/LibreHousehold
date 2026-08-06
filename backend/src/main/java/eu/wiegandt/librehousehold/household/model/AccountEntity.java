package eu.wiegandt.librehousehold.household.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("account")
public record AccountEntity(
        @Id @Column("member_id") UUID memberId,
        @Column("password_hash") String passwordHash
) implements Persistable<UUID> {

    @Override
    public UUID getId() {
        return memberId;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
