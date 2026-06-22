package eu.wiegandt.librehousehold.expenses.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Table(schema = "expenses", value = "expense")
public class ExpenseEntity implements Persistable<UUID> {

    @Id
    private final UUID id;
    @Column("household_id")
    private final UUID householdId;
    private String title;
    private BigDecimal amount;
    @Column("paid_by")
    private UUID paidBy;
    private LocalDate date;
    @Column("category_id")
    private UUID categoryId;
    private String notes;
    @MappedCollection(idColumn = "expense_id")
    private Set<ExpenseSplitRef> splitBetween;
    @Transient
    private boolean isNew = true;

    public ExpenseEntity(UUID id, UUID householdId, String title, BigDecimal amount, UUID paidBy,
                  LocalDate date, UUID categoryId, String notes, Set<ExpenseSplitRef> splitBetween) {
        this.id = id;
        this.householdId = householdId;
        this.title = title;
        this.amount = amount;
        this.paidBy = paidBy;
        this.date = date;
        this.categoryId = categoryId;
        this.notes = notes;
        this.splitBetween = splitBetween != null ? splitBetween : new HashSet<>();
    }

    public void markExisting() {
        this.isNew = false;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setPaidBy(UUID paidBy) {
        this.paidBy = paidBy;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setSplitBetween(Set<ExpenseSplitRef> splitBetween) {
        this.splitBetween = splitBetween;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getPaidBy() {
        return paidBy;
    }

    public LocalDate getDate() {
        return date;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getNotes() {
        return notes;
    }

    public Set<ExpenseSplitRef> getSplitBetween() {
        return splitBetween;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
