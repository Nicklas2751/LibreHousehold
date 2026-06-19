package eu.wiegandt.librehousehold.expenses;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Table(schema = "expenses", value = "reimbursement")
class ReimbursementEntity implements Persistable<UUID> {

    @Id
    private final UUID id;
    @Column("household_id")
    private final UUID householdId;
    private final BigDecimal amount;
    @Column("creditor_id")
    private final UUID creditorId;
    @Column("debtor_id")
    private final UUID debtorId;
    private String status;
    private String notes;
    @Transient
    private boolean isNew = true;

    ReimbursementEntity(UUID id, UUID householdId, BigDecimal amount, UUID creditorId,
                        UUID debtorId, String status, String notes) {
        this.id = id;
        this.householdId = householdId;
        this.amount = amount;
        this.creditorId = creditorId;
        this.debtorId = debtorId;
        this.status = status;
        this.notes = notes;
    }

    void markExisting() {
        this.isNew = false;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getCreditorId() {
        return creditorId;
    }

    public UUID getDebtorId() {
        return debtorId;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
