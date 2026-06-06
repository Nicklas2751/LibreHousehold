package eu.wiegandt.librehousehold.tasks;

import eu.wiegandt.librehousehold.household.HouseholdDeleted;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.Task;
import eu.wiegandt.librehousehold.model.TaskEdit;
import eu.wiegandt.librehousehold.model.TaskStatsByMember;
import eu.wiegandt.librehousehold.model.TaskUpdate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Spy
    private TaskMapper taskMapper = Mappers.getMapper(TaskMapper.class);

    @Mock
    private HouseholdQuery householdQuery;

    @Mock
    private MemberQuery memberQuery;

    @InjectMocks
    private TaskService taskService;

    @Nested
    class getTasks {

        @Test
        void existingHousehold_returnsTaskList() {
            // given
            var householdId = UUID.randomUUID();
            var entity = taskEntity(householdId);
            doReturn(List.of(entity)).when(taskRepository).findByHouseholdId(householdId);
            var expected = new Task(entity.getId(), "Task", LocalDate.of(2024, 7, 1)).recurring(false);

            // when
            var result = taskService.getTasks(householdId);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void noTasks_returnsEmptyList() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(List.of()).when(taskRepository).findByHouseholdId(householdId);

            // when
            var result = taskService.getTasks(householdId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class createTask {

        @Test
        void unknownHousehold_throwsHouseholdNotFoundException() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(false).when(householdQuery).householdExists(householdId);

            // when / then
            assertThatThrownBy(() -> taskService.createTask(householdId, new Task(UUID.randomUUID(), "T", LocalDate.now())))
                    .isInstanceOf(HouseholdNotFoundException.class);
        }

        @Test
        void knownHousehold_savesEntityAndReturnsTask() {
            // given
            var householdId = UUID.randomUUID();
            var taskId = UUID.randomUUID();
            var task = new Task(taskId, "Clean kitchen", LocalDate.of(2024, 7, 1));
            doReturn(true).when(householdQuery).householdExists(householdId);
            var savedEntity = taskEntity(householdId, taskId, "Clean kitchen");
            doReturn(savedEntity).when(taskRepository).save(any(TaskEntity.class));
            var expected = new Task(taskId, "Clean kitchen", LocalDate.of(2024, 7, 1)).recurring(false);

            // when
            var result = taskService.createTask(householdId, task);

            // then
            verify(taskRepository).save(any(TaskEntity.class));
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class updateTask {

        @Test
        void unknownTaskId_throwsTaskNotFoundException() {
            // given
            doReturn(Optional.empty()).when(taskRepository).findById(any());

            // when / then
            assertThatThrownBy(() -> taskService.updateTask(UUID.randomUUID(), new TaskUpdate()))
                    .isInstanceOf(TaskNotFoundException.class);
        }
        @Test
        void nonRecurringTask_setsDoneDate() {
            // given
            var taskId = UUID.randomUUID();
            var doneDate = LocalDate.of(2024, 7, 2);
            var entity = new TaskEntity(taskId, UUID.randomUUID(), null, "T", null, LocalDate.of(2024, 7, 1), null, false, null, null);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            var expected = new Task(taskId, "T", LocalDate.of(2024, 7, 1)).recurring(false).done(doneDate);

            // when
            var result = taskService.updateTask(taskId, new TaskUpdate().done(doneDate));

            // then
            verify(taskRepository).updateDone(taskId, doneDate);
            verify(taskRepository, never()).updateDoneAndDueDate(any(), any(), any());
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void nonRecurringTask_clearsDoneDate() {
            // given
            var taskId = UUID.randomUUID();
            var entity = new TaskEntity(taskId, UUID.randomUUID(), null, "T", null, LocalDate.of(2024, 7, 1), LocalDate.of(2024, 6, 30), false, null, null);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            var expected = new Task(taskId, "T", LocalDate.of(2024, 7, 1)).recurring(false);

            // when
            var result = taskService.updateTask(taskId, new TaskUpdate());

            // then
            verify(taskRepository).clearDone(taskId);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void recurringTask_setsDoneAndAdvancesDueDate() {
            // given
            var taskId = UUID.randomUUID();
            var originalDueDate = LocalDate.of(2024, 7, 1);
            var doneDate = LocalDate.of(2024, 7, 2);
            var expectedNewDueDate = originalDueDate.plusWeeks(2);
            var entity = new TaskEntity(taskId, UUID.randomUUID(), null, "T", null, originalDueDate, null, true, "WEEKS", 2);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            var expected = new Task(taskId, "T", expectedNewDueDate)
                    .recurring(true)
                    .recurrenceUnit(Task.RecurrenceUnitEnum.WEEKS)
                    .recurrenceInterval(2)
                    .done(doneDate);

            // when
            var result = taskService.updateTask(taskId, new TaskUpdate().done(doneDate));

            // then
            verify(taskRepository).updateDoneAndDueDate(taskId, doneDate, expectedNewDueDate);
            verify(taskRepository, never()).updateDone(any(), any());
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void recurringTask_clearsDoneDate_dueDateUnchanged() {
            // given
            var taskId = UUID.randomUUID();
            var dueDate = LocalDate.of(2024, 7, 15);
            var entity = new TaskEntity(taskId, UUID.randomUUID(), null, "T", null, dueDate, LocalDate.of(2024, 7, 2), true, "WEEKS", 2);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            var expected = new Task(taskId, "T", dueDate)
                    .recurring(true)
                    .recurrenceUnit(Task.RecurrenceUnitEnum.WEEKS)
                    .recurrenceInterval(2);

            // when
            var result = taskService.updateTask(taskId, new TaskUpdate());

            // then
            verify(taskRepository).clearDone(taskId);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class editTask {

        @Test
        void unknownTaskId_throwsTaskNotFoundException() {
            // given
            doReturn(Optional.empty()).when(taskRepository).findById(any());

            // when / then
            assertThatThrownBy(() -> taskService.editTask(UUID.randomUUID(), new TaskEdit("Title", LocalDate.now())))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        void validEdit_savesUpdatedEntity() {
            // given
            var taskId = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var entity = new TaskEntity(taskId, householdId, null, "Old title", null, LocalDate.of(2024, 7, 1), null, false, null, null);
            var edit = new TaskEdit("New title", LocalDate.of(2024, 8, 1));
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            doReturn(entity).when(taskRepository).save(entity);

            // when
            taskService.editTask(taskId, edit);

            // then
            verify(taskRepository).save(entity);
        }

        @Test
        void validEdit_returnsUpdatedTask() {
            // given
            var taskId = UUID.randomUUID();
            var householdId = UUID.randomUUID();
            var entity = new TaskEntity(taskId, householdId, null, "Old title", null, LocalDate.of(2024, 7, 1), LocalDate.of(2024, 6, 30), false, null, null);
            var edit = new TaskEdit("New title", LocalDate.of(2024, 8, 1));
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            doReturn(entity).when(taskRepository).save(entity);
            var expected = new Task(taskId, "New title", LocalDate.of(2024, 8, 1))
                    .recurring(false)
                    .done(LocalDate.of(2024, 6, 30));

            // when
            var result = taskService.editTask(taskId, edit);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class deleteTask {

        @Test
        void unknownTaskId_throwsTaskNotFoundException() {
            // given
            doReturn(false).when(taskRepository).existsById(any());

            // when / then
            assertThatThrownBy(() -> taskService.deleteTask(UUID.randomUUID()))
                    .isInstanceOf(TaskNotFoundException.class);
        }

        @Test
        void existingTask_deletesFromRepository() {
            // given
            var taskId = UUID.randomUUID();
            doReturn(true).when(taskRepository).existsById(taskId);

            // when
            taskService.deleteTask(taskId);

            // then
            verify(taskRepository).deleteById(taskId);
        }
    }

    @Nested
    class onHouseholdDeleted {

        @Test
        void publishedEvent_deletesAllTasksOfHousehold() {
            // given
            var householdId = UUID.randomUUID();

            // when
            taskService.onHouseholdDeleted(new HouseholdDeleted(householdId));

            // then
            verify(taskRepository).deleteByHouseholdId(householdId);
        }
    }

    @Nested
    class getTaskStatsByMember {

        @Test
        void noTasks_returnsEmptyList() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(List.of()).when(taskRepository).findByHouseholdId(householdId);

            // when
            var result = taskService.getTaskStatsByMember(householdId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void tasksWithoutAssignment_ignored() {
            // given
            var householdId = UUID.randomUUID();
            var unassigned = new TaskEntity(UUID.randomUUID(), householdId, null, "T", null, LocalDate.now(), null, false, null, null);
            doReturn(List.of(unassigned)).when(taskRepository).findByHouseholdId(householdId);

            // when
            var result = taskService.getTaskStatsByMember(householdId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void nonRecurringDoneTask_countsAsDone() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var doneTask = new TaskEntity(UUID.randomUUID(), householdId, memberId, "T", null, LocalDate.of(2024, 7, 1), LocalDate.of(2024, 7, 2), false, null, null);
            var openTask = new TaskEntity(UUID.randomUUID(), householdId, memberId, "T", null, LocalDate.of(2024, 7, 1), null, false, null, null);
            doReturn(List.of(doneTask, openTask)).when(taskRepository).findByHouseholdId(householdId);
            doReturn(Map.of(memberId, "Alice")).when(memberQuery).findMemberNamesByIds(any());
            var expected = new TaskStatsByMember(memberId, "Alice", 1, 1);

            // when
            var result = taskService.getTaskStatsByMember(householdId);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void recurringTask_doneAfterDueDate_countsAsDone() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            // done >= dueDate → counts as done
            var doneTask = new TaskEntity(UUID.randomUUID(), householdId, memberId, "T", null, LocalDate.of(2024, 6, 30), LocalDate.of(2024, 7, 1), true, "WEEKS", 1);
            doReturn(List.of(doneTask)).when(taskRepository).findByHouseholdId(householdId);
            doReturn(Map.of(memberId, "Bob")).when(memberQuery).findMemberNamesByIds(any());
            var expected = new TaskStatsByMember(memberId, "Bob", 1, 0);

            // when
            var result = taskService.getTaskStatsByMember(householdId);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void recurringTask_doneBeforeDueDate_countsAsOpen() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            // After marking done, dueDate was advanced → done < dueDate → open for next cycle
            var doneBeforeDueDate = new TaskEntity(UUID.randomUUID(), householdId, memberId, "T", null, LocalDate.of(2024, 7, 15), LocalDate.of(2024, 7, 1), true, "WEEKS", 2);
            doReturn(List.of(doneBeforeDueDate)).when(taskRepository).findByHouseholdId(householdId);
            doReturn(Map.of(memberId, "Carol")).when(memberQuery).findMemberNamesByIds(any());
            var expected = new TaskStatsByMember(memberId, "Carol", 0, 1);

            // when
            var result = taskService.getTaskStatsByMember(householdId);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void memberNamesResolvedViaQuery() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var task = new TaskEntity(UUID.randomUUID(), householdId, memberId, "T", null, LocalDate.of(2024, 7, 1), null, false, null, null);
            doReturn(List.of(task)).when(taskRepository).findByHouseholdId(householdId);
            doReturn(Map.of(memberId, "Dave")).when(memberQuery).findMemberNamesByIds(any());
            var expected = new TaskStatsByMember(memberId, "Dave", 0, 1);

            // when
            var result = taskService.getTaskStatsByMember(householdId);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }
    }

    private TaskEntity taskEntity(UUID householdId) {
        return taskEntity(householdId, UUID.randomUUID(), "Task");
    }

    private TaskEntity taskEntity(UUID householdId, UUID id, String title) {
        return new TaskEntity(id, householdId, null, title, null, LocalDate.of(2024, 7, 1), null, false, null, null);
    }
}
