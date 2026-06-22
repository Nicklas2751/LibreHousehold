package eu.wiegandt.librehousehold.tasks.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.UUID;

@Table(schema = "tasks", value = "task_completion")
public record TaskCompletionEntity(
        @Id UUID id,
        @Column("task_id") UUID taskId,
        @Column("done_by") UUID doneBy,
        @Column("done_date") LocalDate doneDate
) implements Persistable<UUID> {

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return true; }
}
