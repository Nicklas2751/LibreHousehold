package eu.wiegandt.librehousehold.tasks.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.UUID;

@Table(schema = "tasks", value = "task")
public class TaskEntity implements Persistable<UUID> {

    @Id
    private final UUID id;
    @Column("household_id")
    private final UUID householdId;
    @Column("assigned_to")
    private UUID assignedTo;
    private String title;
    private String description;
    @Column("due_date")
    private LocalDate dueDate;
    private boolean recurring;
    @Column("recurrence_unit")
    private String recurrenceUnit;
    @Column("recurrence_interval")
    private Integer recurrenceInterval;
    @Transient
    private boolean isNew = true;

    public TaskEntity(UUID id, UUID householdId, UUID assignedTo, String title, String description,
               LocalDate dueDate, boolean recurring, String recurrenceUnit,
               Integer recurrenceInterval) {
        this.id = id;
        this.householdId = householdId;
        this.assignedTo = assignedTo;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.recurring = recurring;
        this.recurrenceUnit = recurrenceUnit;
        this.recurrenceInterval = recurrenceInterval;
    }

    public void markExisting() { this.isNew = false; }

    public void setAssignedTo(UUID assignedTo) { this.assignedTo = assignedTo; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }
    public void setRecurrenceUnit(String recurrenceUnit) { this.recurrenceUnit = recurrenceUnit; }
    public void setRecurrenceInterval(Integer recurrenceInterval) { this.recurrenceInterval = recurrenceInterval; }

    @Override
    public UUID getId() { return id; }
    public UUID getHouseholdId() { return householdId; }
    public UUID getAssignedTo() { return assignedTo; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getDueDate() { return dueDate; }
    public boolean isRecurring() { return recurring; }
    public String getRecurrenceUnit() { return recurrenceUnit; }
    public Integer getRecurrenceInterval() { return recurrenceInterval; }

    @Override
    public boolean isNew() { return isNew; }
}