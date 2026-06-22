package eu.wiegandt.librehousehold.tasks.repository;

import eu.wiegandt.librehousehold.tasks.model.TaskEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends CrudRepository<TaskEntity, UUID> {

    List<TaskEntity> findByHouseholdId(UUID householdId);

    @Modifying
    @Query("UPDATE tasks.task SET due_date = :dueDate WHERE id = :id")
    void updateDueDate(@Param("id") UUID id, @Param("dueDate") LocalDate dueDate);

    @Modifying
    @Query("DELETE FROM tasks.task WHERE household_id = :householdId")
    void deleteByHouseholdId(@Param("householdId") UUID householdId);
}
