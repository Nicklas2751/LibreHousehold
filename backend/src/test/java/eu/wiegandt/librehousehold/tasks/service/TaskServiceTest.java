package eu.wiegandt.librehousehold.tasks.service;
import eu.wiegandt.librehousehold.tasks.exception.*;
import eu.wiegandt.librehousehold.tasks.mapper.*;
import eu.wiegandt.librehousehold.tasks.model.*;
import eu.wiegandt.librehousehold.tasks.repository.*;

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

    @Mock
    private TaskCompletionRepository taskCompletionRepository;

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
            doReturn(List.of()).when(taskCompletionRepository).findLatestByTaskIdIn(any());
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
            doReturn(true).when(householdQuery).householdExists(householdId);
            doReturn(taskEntity(householdId, taskId, "Clean kitchen")).when(taskRepository).save(any(TaskEntity.class));
            var expected = new Task(taskId, "Clean kitchen", LocalDate.of(2024, 7, 1)).recurring(false);

            // when
            var result = taskService.createTask(householdId, new Task(taskId, "Clean kitchen", LocalDate.of(2024, 7, 1)));

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
        void assignedNonRecurringTask_setsDoneDate() {
            // given
            var taskId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var doneDate = LocalDate.of(2024, 7, 2);
            var entity = new TaskEntity(taskId, UUID.randomUUID(), memberId, "T", null, LocalDate.of(2024, 7, 1), false, null, null);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            var expected = new Task(taskId, "T", LocalDate.of(2024, 7, 1)).assignedTo(memberId).recurring(false).done(doneDate);

            // when
            var result = taskService.updateTask(taskId, new TaskUpdate().done(doneDate));

            // then
            verify(taskRepository, never()).updateDueDate(any(), any());
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void assignedNonRecurringTask_clearsDoneDate() {
            // given
            var taskId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var completionId = UUID.randomUUID();
            var completion = new TaskCompletionEntity(completionId, taskId, memberId, LocalDate.of(2024, 6, 30));
            var entity = new TaskEntity(taskId, UUID.randomUUID(), memberId, "T", null, LocalDate.of(2024, 7, 1), false, null, null);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            doReturn(Optional.of(completion)).doReturn(Optional.empty())
                    .when(taskCompletionRepository).findFirstByTaskIdOrderByDoneDateDesc(taskId);
            var expected = new Task(taskId, "T", LocalDate.of(2024, 7, 1)).assignedTo(memberId).recurring(false);

            // when
            var result = taskService.updateTask(taskId, new TaskUpdate());

            // then
            verify(taskCompletionRepository).deleteById(completionId);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void assignedRecurringTask_setsDoneAndAdvancesDueDate() {
            // given
            var taskId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var originalDueDate = LocalDate.of(2024, 7, 1);
            var doneDate = LocalDate.of(2024, 7, 2);
            var expectedNewDueDate = originalDueDate.plusWeeks(2);
            var entity = new TaskEntity(taskId, UUID.randomUUID(), memberId, "T", null, originalDueDate, true, "WEEKS", 2);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            var expected = new Task(taskId, "T", expectedNewDueDate)
                    .assignedTo(memberId)
                    .recurring(true)
                    .recurrenceUnit(Task.RecurrenceUnitEnum.WEEKS)
                    .recurrenceInterval(2)
                    .done(doneDate);

            // when
            var result = taskService.updateTask(taskId, new TaskUpdate().done(doneDate));

            // then
            verify(taskRepository).updateDueDate(taskId, expectedNewDueDate);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void assignedRecurringTask_clearsDoneDate_dueDateUnchanged() {
            // given
            var taskId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var completionId = UUID.randomUUID();
            var dueDate = LocalDate.of(2024, 7, 15);
            var completion = new TaskCompletionEntity(completionId, taskId, memberId, LocalDate.of(2024, 7, 2));
            var entity = new TaskEntity(taskId, UUID.randomUUID(), memberId, "T", null, dueDate, true, "WEEKS", 2);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            doReturn(Optional.of(completion)).doReturn(Optional.empty())
                    .when(taskCompletionRepository).findFirstByTaskIdOrderByDoneDateDesc(taskId);
            var expected = new Task(taskId, "T", dueDate)
                    .assignedTo(memberId)
                    .recurring(true)
                    .recurrenceUnit(Task.RecurrenceUnitEnum.WEEKS)
                    .recurrenceInterval(2);

            // when
            var result = taskService.updateTask(taskId, new TaskUpdate());

            // then
            verify(taskRepository, never()).updateDueDate(any(), any());
            verify(taskCompletionRepository).deleteById(completionId);
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void assignedTask_setsDone_savesCompletion() {
            // given
            var taskId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var doneDate = LocalDate.of(2024, 7, 5);
            var entity = new TaskEntity(taskId, UUID.randomUUID(), memberId, "T", null, LocalDate.of(2024, 7, 1), false, null, null);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);

            // when
            taskService.updateTask(taskId, new TaskUpdate().done(doneDate));

            // then
            verify(taskCompletionRepository).save(argThat(c ->
                    c.taskId().equals(taskId) &&
                    c.doneBy().equals(memberId) &&
                    c.doneDate().equals(doneDate)
            ));
        }

        @Test
        void assignedTask_clearsDone_deletesLatestCompletion() {
            // given
            var taskId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var completionId = UUID.randomUUID();
            var completion = new TaskCompletionEntity(completionId, taskId, memberId, LocalDate.of(2024, 7, 5));
            var entity = new TaskEntity(taskId, UUID.randomUUID(), memberId, "T", null, LocalDate.of(2024, 7, 1), false, null, null);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            doReturn(Optional.of(completion)).doReturn(Optional.empty())
                    .when(taskCompletionRepository).findFirstByTaskIdOrderByDoneDateDesc(taskId);

            // when
            taskService.updateTask(taskId, new TaskUpdate());

            // then
            verify(taskCompletionRepository).deleteById(completionId);
        }

        @Test
        void unassignedTask_setsDone_noCompletionSaved() {
            // given
            var taskId = UUID.randomUUID();
            var entity = new TaskEntity(taskId, UUID.randomUUID(), null, "T", null, LocalDate.of(2024, 7, 1), false, null, null);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);

            // when
            taskService.updateTask(taskId, new TaskUpdate().done(LocalDate.of(2024, 7, 5)));

            // then
            verify(taskCompletionRepository, never()).save(any());
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
            var entity = new TaskEntity(taskId, UUID.randomUUID(), null, "Old title", null, LocalDate.of(2024, 7, 1), false, null, null);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            doReturn(entity).when(taskRepository).save(entity);

            // when
            taskService.editTask(taskId, new TaskEdit("New title", LocalDate.of(2024, 8, 1)));

            // then
            verify(taskRepository).save(entity);
        }

        @Test
        void validEdit_returnsUpdatedTask() {
            // given
            var taskId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var previousDoneDate = LocalDate.of(2024, 6, 30);
            var entity = new TaskEntity(taskId, UUID.randomUUID(), null, "Old title", null, LocalDate.of(2024, 7, 1), false, null, null);
            doReturn(Optional.of(entity)).when(taskRepository).findById(taskId);
            doReturn(entity).when(taskRepository).save(entity);
            doReturn(Optional.of(new TaskCompletionEntity(UUID.randomUUID(), taskId, memberId, previousDoneDate)))
                    .when(taskCompletionRepository).findFirstByTaskIdOrderByDoneDateDesc(taskId);
            var expected = new Task(taskId, "New title", LocalDate.of(2024, 8, 1))
                    .recurring(false)
                    .done(previousDoneDate);

            // when
            var result = taskService.editTask(taskId, new TaskEdit("New title", LocalDate.of(2024, 8, 1)));

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
        void noCompletionsAndNoOpenTasks_returnsEmpty() {
            // given
            var householdId = UUID.randomUUID();
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);
            doReturn(List.of()).when(taskCompletionRepository).findByHouseholdIdAndPeriod(householdId, from, to);
            doReturn(List.of()).when(taskRepository).findByHouseholdId(householdId);

            // when
            var result = taskService.getTaskStatsByMember(householdId, from, to);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void unassignedOpenTask_ignored() {
            // given
            var householdId = UUID.randomUUID();
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);
            var unassigned = new TaskEntity(UUID.randomUUID(), householdId, null, "T", null, LocalDate.of(2024, 7, 1), false, null, null);
            doReturn(List.of()).when(taskCompletionRepository).findByHouseholdIdAndPeriod(householdId, from, to);
            doReturn(List.of(unassigned)).when(taskRepository).findByHouseholdId(householdId);

            // when
            var result = taskService.getTaskStatsByMember(householdId, from, to);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void assignedTaskWithoutCompletion_countedAsOpen() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);
            var openTask = new TaskEntity(UUID.randomUUID(), householdId, memberId, "T", null, LocalDate.of(2024, 7, 1), false, null, null);
            doReturn(List.of()).when(taskCompletionRepository).findByHouseholdIdAndPeriod(householdId, from, to);
            doReturn(List.of(openTask)).when(taskRepository).findByHouseholdId(householdId);
            doReturn(List.of()).when(taskCompletionRepository).findLatestByTaskIdIn(any());
            doReturn(Map.of(memberId, "Alice")).when(memberQuery).findMemberNamesByIds(any());
            var expected = new TaskStatsByMember(memberId, "Alice", 0, 1);

            // when
            var result = taskService.getTaskStatsByMember(householdId, from, to);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void oneCompletionInPeriod_doneCountIsOne() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var taskId = UUID.randomUUID();
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);
            var completion = new TaskCompletionEntity(UUID.randomUUID(), taskId, memberId, LocalDate.of(2024, 7, 5));
            doReturn(List.of(completion)).when(taskCompletionRepository).findByHouseholdIdAndPeriod(householdId, from, to);
            doReturn(List.of()).when(taskRepository).findByHouseholdId(householdId);
            doReturn(Map.of(memberId, "Bob")).when(memberQuery).findMemberNamesByIds(any());
            var expected = new TaskStatsByMember(memberId, "Bob", 1, 0);

            // when
            var result = taskService.getTaskStatsByMember(householdId, from, to);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void twoCompletionsSameMember_doneCountIsTwo() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var taskId = UUID.randomUUID();
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);
            var completion1 = new TaskCompletionEntity(UUID.randomUUID(), taskId, memberId, LocalDate.of(2024, 7, 5));
            var completion2 = new TaskCompletionEntity(UUID.randomUUID(), taskId, memberId, LocalDate.of(2024, 7, 12));
            doReturn(List.of(completion1, completion2)).when(taskCompletionRepository).findByHouseholdIdAndPeriod(householdId, from, to);
            doReturn(List.of()).when(taskRepository).findByHouseholdId(householdId);
            doReturn(Map.of(memberId, "Carol")).when(memberQuery).findMemberNamesByIds(any());
            var expected = new TaskStatsByMember(memberId, "Carol", 2, 0);

            // when
            var result = taskService.getTaskStatsByMember(householdId, from, to);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void nonRecurringAssignedTaskWithCompletion_notCountedAsOpen() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var taskId = UUID.randomUUID();
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);
            var completion = new TaskCompletionEntity(UUID.randomUUID(), taskId, memberId, LocalDate.of(2024, 7, 5));
            var assignedTask = new TaskEntity(taskId, householdId, memberId, "T", null, LocalDate.of(2024, 7, 1), false, null, null);
            doReturn(List.of(completion)).when(taskCompletionRepository).findByHouseholdIdAndPeriod(householdId, from, to);
            doReturn(List.of(assignedTask)).when(taskRepository).findByHouseholdId(householdId);
            doReturn(List.of(completion)).when(taskCompletionRepository).findLatestByTaskIdIn(any());
            doReturn(Map.of(memberId, "Alice")).when(memberQuery).findMemberNamesByIds(any());
            var expected = new TaskStatsByMember(memberId, "Alice", 1, 0);

            // when
            var result = taskService.getTaskStatsByMember(householdId, from, to);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void recurringAssignedTaskWithCompletionInCurrentCycle_notCountedAsOpen() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var taskId = UUID.randomUUID();
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);
            // dueDate already advanced to July 8; completion on July 5 is >= July 8 - 7 = July 1 → in current cycle
            var completion = new TaskCompletionEntity(UUID.randomUUID(), taskId, memberId, LocalDate.of(2024, 7, 5));
            var recurringTask = new TaskEntity(taskId, householdId, memberId, "T", null, LocalDate.of(2024, 7, 8), true, "WEEKS", 1);
            doReturn(List.of(completion)).when(taskCompletionRepository).findByHouseholdIdAndPeriod(householdId, from, to);
            doReturn(List.of(recurringTask)).when(taskRepository).findByHouseholdId(householdId);
            doReturn(List.of(completion)).when(taskCompletionRepository).findLatestByTaskIdIn(any());
            doReturn(Map.of(memberId, "Alice")).when(memberQuery).findMemberNamesByIds(any());
            var expected = new TaskStatsByMember(memberId, "Alice", 1, 0);

            // when
            var result = taskService.getTaskStatsByMember(householdId, from, to);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void recurringAssignedTaskWithCompletionFromPreviousCycle_countedAsOpen() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var taskId = UUID.randomUUID();
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);
            // dueDate already advanced to July 15; completion on July 5 is < July 15 - 7 = July 8 → old cycle
            var oldCompletion = new TaskCompletionEntity(UUID.randomUUID(), taskId, memberId, LocalDate.of(2024, 7, 5));
            var recurringTask = new TaskEntity(taskId, householdId, memberId, "T", null, LocalDate.of(2024, 7, 15), true, "WEEKS", 1);
            doReturn(List.of()).when(taskCompletionRepository).findByHouseholdIdAndPeriod(householdId, from, to);
            doReturn(List.of(recurringTask)).when(taskRepository).findByHouseholdId(householdId);
            doReturn(List.of(oldCompletion)).when(taskCompletionRepository).findLatestByTaskIdIn(any());
            doReturn(Map.of(memberId, "Alice")).when(memberQuery).findMemberNamesByIds(any());
            var expected = new TaskStatsByMember(memberId, "Alice", 0, 1);

            // when
            var result = taskService.getTaskStatsByMember(householdId, from, to);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }
    }

    private TaskEntity taskEntity(UUID householdId) {
        return taskEntity(householdId, UUID.randomUUID(), "Task");
    }

    private TaskEntity taskEntity(UUID householdId, UUID id, String title) {
        return new TaskEntity(id, householdId, null, title, null, LocalDate.of(2024, 7, 1), false, null, null);
    }
}
