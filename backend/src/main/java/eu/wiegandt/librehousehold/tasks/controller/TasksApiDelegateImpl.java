package eu.wiegandt.librehousehold.tasks.controller;

import eu.wiegandt.librehousehold.api.TasksApiDelegate;
import eu.wiegandt.librehousehold.auth.CurrentUserIdProvider;
import eu.wiegandt.librehousehold.auth.InHousehold;
import eu.wiegandt.librehousehold.tasks.exception.TaskBodyIsRequiredException;
import eu.wiegandt.librehousehold.tasks.service.TaskService;
import eu.wiegandt.librehousehold.model.Task;
import eu.wiegandt.librehousehold.model.TaskEdit;
import eu.wiegandt.librehousehold.model.TaskUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TasksApiDelegateImpl implements TasksApiDelegate {

    private final TaskService taskService;
    private final CurrentUserIdProvider currentUserIdProvider;

    public TasksApiDelegateImpl(TaskService taskService, CurrentUserIdProvider currentUserIdProvider) {
        this.taskService = taskService;
        this.currentUserIdProvider = currentUserIdProvider;
    }

    @Override
    @InHousehold
    public ResponseEntity<List<Task>> getTasks(UUID householdId) {
        return ResponseEntity.ok(taskService.getTasks(householdId));
    }

    @Override
    @InHousehold
    public ResponseEntity<Task> createTask(UUID householdId, Optional<Task> task) {
        var t = task.orElseThrow(TaskBodyIsRequiredException::new);
        return ResponseEntity.ok(taskService.createTask(householdId, t));
    }

    @Override
    @InHousehold
    public ResponseEntity<Task> updateTask(UUID householdId, UUID taskId, Optional<TaskUpdate> taskUpdate) {
        var update = taskUpdate.orElseThrow(TaskBodyIsRequiredException::new);
        return ResponseEntity.ok(taskService.updateTask(taskId, update, currentUserIdProvider.getCurrentUserId()));
    }

    @Override
    @InHousehold
    public ResponseEntity<Task> editTask(UUID householdId, UUID taskId, TaskEdit taskEdit) {
        return ResponseEntity.ok(taskService.editTask(taskId, taskEdit));
    }

    @Override
    @InHousehold
    public ResponseEntity<Void> deleteTask(UUID householdId, UUID taskId) {
        taskService.deleteTask(taskId);
        return ResponseEntity.noContent().build();
    }
}
