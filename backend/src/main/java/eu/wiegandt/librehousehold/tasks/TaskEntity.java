package eu.wiegandt.librehousehold.tasks;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.UUID;

@Table(schema = "tasks", value = "task")
class TaskEntity implements Persistable<UUID> {

    @Id
    private final UUID id;
    @Column("household_id")
    private final UUID householdId;
    @Column("assigned_to")
    private final UUID assignedTo;
    private final String title;
    private final String description;
    @Column("due_date")
    private LocalDate dueDate;
    private LocalDate done;
    private final boolean recurring;
    @Column("recurrence_unit")
    private final String recurrenceUnit;
    @Column("recurrence_interval")
    private final Integer recurrenceInterval;

    TaskEntity(UUID id, UUID householdId, UUID assignedTo, String title, String description,
               LocalDate dueDate, LocalDate done, boolean recurring, String recurrenceUnit,
               Integer recurrenceInterval) {
        this.id = id;
        this.householdId = householdId;
        this.assignedTo = assignedTo;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.done = done;
        this.recurring = recurring;
        this.recurrenceUnit = recurrenceUnit;
        this.recurrenceInterval = recurrenceInterval;
    }

    public void setDone(LocalDate done) { this.done = done; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    @Override
    public UUID getId() { return id; }
    public UUID getHouseholdId() { return householdId; }
    public UUID getAssignedTo() { return assignedTo; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getDone() { return done; }
    public boolean isRecurring() { return recurring; }
    public String getRecurrenceUnit() { return recurrenceUnit; }
    public Integer getRecurrenceInterval() { return recurrenceInterval; }

    @Override
    public boolean isNew() { return true; }
}