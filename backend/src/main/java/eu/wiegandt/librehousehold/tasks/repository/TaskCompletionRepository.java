package eu.wiegandt.librehousehold.tasks.repository;

import eu.wiegandt.librehousehold.tasks.model.TaskCompletionEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskCompletionRepository extends CrudRepository<TaskCompletionEntity, UUID> {

    @Query("""
            SELECT tc.id, tc.task_id, tc.done_by, tc.done_date
            FROM tasks.task_completion tc
            JOIN tasks.task t ON tc.task_id = t.id
            WHERE t.household_id = :householdId
              AND tc.done_date >= :from
              AND tc.done_date <= :to
            """)
    List<TaskCompletionEntity> findByHouseholdIdAndPeriod(
            @Param("householdId") UUID householdId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    void deleteByTaskIdAndDoneDate(UUID taskId, LocalDate doneDate);

    Optional<TaskCompletionEntity> findFirstByTaskIdOrderByDoneDateDesc(UUID taskId);

    @Query("SELECT DISTINCT ON (task_id) id, task_id, done_by, done_date FROM tasks.task_completion WHERE task_id IN (:taskIds) ORDER BY task_id, done_date DESC")
    List<TaskCompletionEntity> findLatestByTaskIdIn(@Param("taskIds") Collection<UUID> taskIds);
}
