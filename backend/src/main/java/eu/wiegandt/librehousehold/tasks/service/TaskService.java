package eu.wiegandt.librehousehold.tasks.service;

import eu.wiegandt.librehousehold.household.HouseholdDeleted;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.tasks.TaskStatisticsProvider;
import eu.wiegandt.librehousehold.tasks.exception.HouseholdNotFoundException;
import eu.wiegandt.librehousehold.tasks.exception.TaskNotFoundException;
import eu.wiegandt.librehousehold.tasks.mapper.TaskMapper;
import eu.wiegandt.librehousehold.tasks.model.TaskCompletionEntity;
import eu.wiegandt.librehousehold.tasks.model.TaskEntity;
import eu.wiegandt.librehousehold.tasks.repository.TaskCompletionRepository;
import eu.wiegandt.librehousehold.tasks.repository.TaskRepository;
import eu.wiegandt.librehousehold.model.Task;
import eu.wiegandt.librehousehold.model.TaskEdit;
import eu.wiegandt.librehousehold.model.TaskStatsByMember;
import eu.wiegandt.librehousehold.model.TaskUpdate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@Service
public class TaskService implements TaskStatisticsProvider {

    private final TaskRepository taskRepository;
    private final TaskCompletionRepository taskCompletionRepository;
    private final TaskMapper taskMapper;
    private final HouseholdQuery householdQuery;
    private final MemberQuery memberQuery;

    public TaskService(TaskRepository taskRepository,
                       TaskCompletionRepository taskCompletionRepository,
                       TaskMapper taskMapper,
                       HouseholdQuery householdQuery,
                       MemberQuery memberQuery) {
        this.taskRepository = taskRepository;
        this.taskCompletionRepository = taskCompletionRepository;
        this.taskMapper = taskMapper;
        this.householdQuery = householdQuery;
        this.memberQuery = memberQuery;
    }

    public List<Task> getTasks(UUID householdId) {
        var entities = taskRepository.findByHouseholdId(householdId);
        if (entities.isEmpty()) {
            return List.of();
        }
        var taskIds = entities.stream().map(TaskEntity::getId).toList();
        var latestDoneByTaskId = taskCompletionRepository.findLatestByTaskIdIn(taskIds).stream()
                .collect(toMap(TaskCompletionEntity::taskId, TaskCompletionEntity::doneDate));
        return entities.stream()
                .map(e -> taskMapper.toTask(e).done(latestDoneByTaskId.get(e.getId())))
                .toList();
    }

    public Task createTask(UUID householdId, Task task) {
        if (!householdQuery.householdExists(householdId)) {
            throw new HouseholdNotFoundException();
        }
        var saved = taskRepository.save(taskMapper.toEntity(task, householdId));
        return taskMapper.toTask(saved);
    }

    public Task updateTask(UUID taskId, TaskUpdate update, UUID currentUserId) {
        var entity = taskRepository.findById(taskId)
                .orElseThrow(TaskNotFoundException::new);

        var done = update.getDone();
        if (done.isPresent()) {
            var doneDate = done.get();
            if (entity.isRecurring() && entity.getRecurrenceUnit() != null && entity.getRecurrenceInterval() != null) {
                var newDueDate = entity.getDueDate().plus(entity.getRecurrenceInterval(), ChronoUnit.valueOf(entity.getRecurrenceUnit()));
                entity.setDueDate(newDueDate);
                taskRepository.updateDueDate(taskId, newDueDate);
            }
            taskCompletionRepository.save(new TaskCompletionEntity(UUID.randomUUID(), taskId, currentUserId, doneDate));
            return taskMapper.toTask(entity).done(doneDate);
        } else {
            taskCompletionRepository.findFirstByTaskIdOrderByDoneDateDesc(taskId)
                    .ifPresent(c -> taskCompletionRepository.deleteById(c.id()));
            var latestDone = taskCompletionRepository.findFirstByTaskIdOrderByDoneDateDesc(taskId)
                    .map(TaskCompletionEntity::doneDate).orElse(null);
            return taskMapper.toTask(entity).done(latestDone);
        }
    }

    public Task editTask(UUID taskId, TaskEdit edit) {
        var existing = taskRepository.findById(taskId)
                .orElseThrow(TaskNotFoundException::new);
        taskMapper.updateEntityFromEdit(edit, existing);
        var saved = taskRepository.save(existing);
        var latestDone = taskCompletionRepository.findFirstByTaskIdOrderByDoneDateDesc(taskId)
                .map(TaskCompletionEntity::doneDate).orElse(null);
        return taskMapper.toTask(saved).done(latestDone);
    }

    public void deleteTask(UUID taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new TaskNotFoundException();
        }
        taskRepository.deleteById(taskId);
    }

    @ApplicationModuleListener
    void onHouseholdDeleted(HouseholdDeleted event) {
        taskRepository.deleteByHouseholdId(event.householdId());
    }

    @Override
    public List<TaskStatsByMember> getTaskStatsByMember(UUID householdId, LocalDate from, LocalDate to) {
        var completions = taskCompletionRepository.findByHouseholdIdAndPeriod(householdId, from, to);
        var doneCountByMember = completions.stream()
                .collect(groupingBy(TaskCompletionEntity::doneBy, counting()));

        var tasks = taskRepository.findByHouseholdId(householdId);
        var assignedTasks = tasks.stream().filter(t -> t.getAssignedTo() != null).toList();
        var taskIds = assignedTasks.stream().map(TaskEntity::getId).toList();
        var latestDoneByTaskId = taskIds.isEmpty() ? Map.<UUID, LocalDate>of() :
                taskCompletionRepository.findLatestByTaskIdIn(taskIds).stream()
                        .collect(toMap(TaskCompletionEntity::taskId, TaskCompletionEntity::doneDate));
        var openCountByMember = assignedTasks.stream()
                .filter(t -> !isCurrentlyDone(t, latestDoneByTaskId.get(t.getId())))
                .collect(groupingBy(TaskEntity::getAssignedTo, counting()));

        var allMemberIds = new HashSet<UUID>();
        allMemberIds.addAll(doneCountByMember.keySet());
        allMemberIds.addAll(openCountByMember.keySet());

        if (allMemberIds.isEmpty()) {
            return List.of();
        }

        var memberNames = memberQuery.findMemberNamesByIds(allMemberIds);

        return allMemberIds.stream()
                .map(memberId -> new TaskStatsByMember(
                        memberId,
                        memberNames.getOrDefault(memberId, "Unknown"),
                        doneCountByMember.getOrDefault(memberId, 0L).intValue(),
                        openCountByMember.getOrDefault(memberId, 0L).intValue()
                ))
                .toList();
    }

    private boolean isCurrentlyDone(TaskEntity task, LocalDate latestDoneDate) {
        if (latestDoneDate == null) return false;
        if (!task.isRecurring()) return true;
        var previousDueDate = task.getDueDate().minus(task.getRecurrenceInterval(), ChronoUnit.valueOf(task.getRecurrenceUnit()));
        return !latestDoneDate.isBefore(previousDueDate);
    }
}
