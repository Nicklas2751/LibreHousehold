package eu.wiegandt.librehousehold.tasks.controller;

import eu.wiegandt.librehousehold.api.TasksApiDelegate;
import eu.wiegandt.librehousehold.tasks.exception.TaskBodyIsRequiredException;
import eu.wiegandt.librehousehold.tasks.service.TaskService;
import eu.wiegandt.librehousehold.model.Task;
import eu.wiegandt.librehousehold.model.TaskEdit;
import eu.wiegandt.librehousehold.model.TaskUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TasksApiDelegateImpl implements TasksApiDelegate {

    private final TaskService taskService;

    public TasksApiDelegateImpl(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isMember(#householdId, authentication)")
    public ResponseEntity<List<Task>> getTasks(UUID householdId) {
        return ResponseEntity.ok(taskService.getTasks(householdId));
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isMember(#householdId, authentication)")
    public ResponseEntity<Task> createTask(UUID householdId, Optional<Task> task) {
        var t = task.orElseThrow(TaskBodyIsRequiredException::new);
        return ResponseEntity.ok(taskService.createTask(householdId, t));
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isMember(#householdId, authentication)")
    public ResponseEntity<Task> updateTask(UUID householdId, UUID taskId, Optional<TaskUpdate> taskUpdate) {
        var update = taskUpdate.orElseThrow(TaskBodyIsRequiredException::new);
        return ResponseEntity.ok(taskService.updateTask(householdId, taskId, update));
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isMember(#householdId, authentication)")
    public ResponseEntity<Task> editTask(UUID householdId, UUID taskId, TaskEdit taskEdit) {
        return ResponseEntity.ok(taskService.editTask(householdId, taskId, taskEdit));
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isMember(#householdId, authentication)")
    public ResponseEntity<Void> deleteTask(UUID householdId, UUID taskId) {
        taskService.deleteTask(householdId, taskId);
        return ResponseEntity.noContent().build();
    }
}
