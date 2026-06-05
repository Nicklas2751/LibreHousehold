package eu.wiegandt.librehousehold.tasks;

import eu.wiegandt.librehousehold.api.TasksApiDelegate;
import eu.wiegandt.librehousehold.model.Task;
import eu.wiegandt.librehousehold.model.TaskUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TasksApiDelegateImpl implements TasksApiDelegate {

    private final TaskService taskService;

    TasksApiDelegateImpl(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public ResponseEntity<List<Task>> getTasks(UUID householdId) {
        return ResponseEntity.ok(taskService.getTasks(householdId));
    }

    @Override
    public ResponseEntity<Task> createTask(UUID householdId, Optional<Task> task) {
        var t = task.orElseThrow(TaskBodyIsRequiredException::new);
        return ResponseEntity.ok(taskService.createTask(householdId, t));
    }

    @Override
    public ResponseEntity<Task> updateTask(UUID householdId, UUID taskId, Optional<TaskUpdate> taskUpdate) {
        var update = taskUpdate.orElseThrow(TaskBodyIsRequiredException::new);
        return ResponseEntity.ok(taskService.updateTask(taskId, update));
    }
}
