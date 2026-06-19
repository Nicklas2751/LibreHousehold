package eu.wiegandt.librehousehold.expenses;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(schema = "expenses", value = "category")
class CategoryEntity implements Persistable<UUID> {

    @Id
    private final UUID id;
    @Column("household_id")
    private final UUID householdId;
    private String name;
    private String icon;
    @Transient
    private boolean isNew = true;

    CategoryEntity(UUID id, UUID householdId, String name, String icon) {
        this.id = id;
        this.householdId = householdId;
        this.name = name;
        this.icon = icon;
    }

    void markExisting() { this.isNew = false; }

    @Override
    public UUID getId() { return id; }
    public UUID getHouseholdId() { return householdId; }
    public String getName() { return name; }
    public String getIcon() { return icon; }

    @Override
    public boolean isNew() { return isNew; }
}
