package eu.wiegandt.librehousehold.tasks.service;

import eu.wiegandt.librehousehold.household.HouseholdDeleted;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.tasks.exception.HouseholdNotFoundException;
import eu.wiegandt.librehousehold.tasks.exception.TaskNotFoundException;
import eu.wiegandt.librehousehold.tasks.mapper.TaskMapper;
import eu.wiegandt.librehousehold.tasks.model.TaskEntity;
import eu.wiegandt.librehousehold.tasks.repository.TaskRepository;
import eu.wiegandt.librehousehold.model.Task;
import eu.wiegandt.librehousehold.model.TaskEdit;
import eu.wiegandt.librehousehold.model.TaskStatsByMember;
import eu.wiegandt.librehousehold.model.TaskUpdate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.groupingBy;

@Service
public class TaskService implements TaskStatisticsProvider {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final HouseholdQuery householdQuery;
    private final MemberQuery memberQuery;

    public TaskService(TaskRepository taskRepository,
                TaskMapper taskMapper,
                HouseholdQuery householdQuery,
                MemberQuery memberQuery) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.householdQuery = householdQuery;
        this.memberQuery = memberQuery;
    }

    public List<Task> getTasks(UUID householdId) {
        return taskRepository.findByHouseholdId(householdId).stream()
                .map(taskMapper::toTask)
                .toList();
    }

    public Task createTask(UUID householdId, Task task) {
        if (!householdQuery.householdExists(householdId)) {
            throw new HouseholdNotFoundException();
        }
        var saved = taskRepository.save(taskMapper.toEntity(task, householdId));
        return taskMapper.toTask(saved);
    }

    public Task updateTask(UUID taskId, TaskUpdate update) {
        var entity = taskRepository.findById(taskId)
                .orElseThrow(TaskNotFoundException::new);

        var done = update.getDone();
        if (done.isPresent()) {
            var doneDate = done.get();
            entity.setDone(doneDate);
            if (entity.isRecurring() && entity.getRecurrenceUnit() != null && entity.getRecurrenceInterval() != null) {
                var newDueDate = entity.getDueDate().plus(entity.getRecurrenceInterval(), ChronoUnit.valueOf(entity.getRecurrenceUnit()));
                entity.setDueDate(newDueDate);
                taskRepository.updateDoneAndDueDate(taskId, doneDate, newDueDate);
            } else {
                taskRepository.updateDone(taskId, doneDate);
            }
        } else {
            entity.setDone(null);
            taskRepository.clearDone(taskId);
        }

        return taskMapper.toTask(entity);
    }

    public Task editTask(UUID taskId, TaskEdit edit) {
        var existing = taskRepository.findById(taskId)
                .orElseThrow(TaskNotFoundException::new);
        taskMapper.updateEntityFromEdit(edit, existing);
        var saved = taskRepository.save(existing);
        return taskMapper.toTask(saved);
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
    public List<TaskStatsByMember> getTaskStatsByMember(UUID householdId) {
        var tasks = taskRepository.findByHouseholdId(householdId);
        var tasksByMember = tasks.stream()
                .filter(t -> t.getAssignedTo() != null)
                .collect(groupingBy(TaskEntity::getAssignedTo));

        if (tasksByMember.isEmpty()) {
            return List.of();
        }

        var memberNames = memberQuery.findMemberNamesByIds(tasksByMember.keySet());

        return tasksByMember.entrySet().stream()
                .map(entry -> {
                    var memberId = entry.getKey();
                    var memberTasks = entry.getValue();
                    var doneCount = (int) memberTasks.stream().filter(this::isDone).count();
                    return new TaskStatsByMember(
                            memberId,
                            memberNames.getOrDefault(memberId, "Unknown"),
                            doneCount,
                            memberTasks.size() - doneCount
                    );
                })
                .toList();
    }

    private boolean isDone(TaskEntity task) {
        if (task.getDone() == null) return false;
        if (!task.isRecurring()) return true;
        return !task.getDone().isBefore(task.getDueDate());
    }
}
