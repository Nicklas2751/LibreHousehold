package eu.wiegandt.librehousehold.tasks.controller;

import eu.wiegandt.librehousehold.core.CurrentUserIdProvider;
import eu.wiegandt.librehousehold.model.Task;
import eu.wiegandt.librehousehold.model.TaskUpdate;
import eu.wiegandt.librehousehold.tasks.exception.TaskBodyIsRequiredException;
import eu.wiegandt.librehousehold.tasks.service.TaskService;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class TasksApiDelegateImplTest {

    @Mock
    private TaskService taskService;

    @Mock
    private CurrentUserIdProvider currentUserIdProvider;

    @InjectMocks
    private TasksApiDelegateImpl tasksApiDelegate;

    @Nested
    class getTasks {

        @Test
        void householdId_delegatesToService_returns200WithList() {
            // given
            var householdId = UUID.randomUUID();
            var tasks = Instancio.ofList(Task.class).size(2).create();
            doReturn(tasks).when(taskService).getTasks(householdId);

            // when
            var result = tasksApiDelegate.getTasks(householdId);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isEqualTo(tasks);
        }
    }

    @Nested
    class createTask {

        @Test
        void emptyBody_throwsTaskBodyIsRequiredException() {
            // when / then
            assertThatThrownBy(() -> tasksApiDelegate.createTask(UUID.randomUUID(), Optional.empty()))
                    .isInstanceOf(TaskBodyIsRequiredException.class);
        }

        @Test
        void validBody_delegatesToService_returns200() {
            // given
            var householdId = UUID.randomUUID();
            var task = Instancio.create(Task.class);
            var created = Instancio.create(Task.class);
            doReturn(created).when(taskService).createTask(householdId, task);

            // when
            var result = tasksApiDelegate.createTask(householdId, Optional.of(task));

            // then
            verify(taskService).createTask(householdId, task);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isEqualTo(created);
        }
    }

    @Nested
    class updateTask {

        @Test
        void emptyBody_throwsTaskBodyIsRequiredException() {
            // when / then
            assertThatThrownBy(() -> tasksApiDelegate.updateTask(UUID.randomUUID(), UUID.randomUUID(), Optional.empty()))
                    .isInstanceOf(TaskBodyIsRequiredException.class);
        }

        @Test
        void validBody_delegatesToService_returns200() {
            // given
            var householdId = UUID.randomUUID();
            var taskId = UUID.randomUUID();
            var currentUserId = UUID.randomUUID();
            var update = Instancio.create(TaskUpdate.class);
            var updated = Instancio.create(Task.class);
            doReturn(currentUserId).when(currentUserIdProvider).getCurrentUserId();
            doReturn(updated).when(taskService).updateTask(taskId, update, currentUserId);

            // when
            var result = tasksApiDelegate.updateTask(householdId, taskId, Optional.of(update));

            // then
            verify(taskService).updateTask(taskId, update, currentUserId);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isEqualTo(updated);
        }
    }
}
