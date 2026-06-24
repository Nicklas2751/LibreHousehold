package eu.wiegandt.librehousehold.auth.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(schema = "auth", value = "federated_identity")
public record FederatedIdentityEntity(
        @Id UUID id,
        @Column("account_id") UUID accountId,
        String provider,
        @Column("provider_sub") String providerSub
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
