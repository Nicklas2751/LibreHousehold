package eu.wiegandt.librehousehold.tasks.service;
import eu.wiegandt.librehousehold.tasks.exception.*;
import eu.wiegandt.librehousehold.tasks.mapper.*;
import eu.wiegandt.librehousehold.tasks.model.*;
import eu.wiegandt.librehousehold.tasks.repository.*;

import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.Task;
import eu.wiegandt.librehousehold.model.TaskStatsByMember;
import eu.wiegandt.librehousehold.model.TaskUpdate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TaskMapperImpl.class, TaskService.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration"
})
class TaskServiceIT {

    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskCompletionRepository taskCompletionRepository;

    @MockitoBean
    private HouseholdQuery householdQuery;

    @MockitoBean
    private MemberQuery memberQuery;

    @Nested
    class createTask {

        @Test
        void validTask_persistedInDatabase() {
            // given
            var householdId = UUID.randomUUID();
            var taskId = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);

            // when
            taskService.createTask(householdId, new Task(taskId, "Take out trash", LocalDate.of(2024, 7, 1)));

            // then
            assertThat(taskRepository.findById(taskId))
                    .hasValueSatisfying(e -> {
                        assertThat(e.getTitle()).isEqualTo("Take out trash");
                        assertThat(e.getHouseholdId()).isEqualTo(householdId);
                    });
        }
    }

    @Nested
    class getTasks {

        @Test
        void existingTasks_returnsAll() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);
            taskService.createTask(householdId, new Task(UUID.randomUUID(), "Task 1", LocalDate.of(2024, 7, 1)));
            taskService.createTask(householdId, new Task(UUID.randomUUID(), "Task 2", LocalDate.of(2024, 7, 1)));

            // when
            var result = taskService.getTasks(householdId);

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class updateTask {

        @Test
        void nonRecurringTask_savesCompletionWithCurrentUserInDatabase() {
            // given
            var householdId = UUID.randomUUID();
            var taskId = UUID.randomUUID();
            var currentUserId = UUID.randomUUID();
            var doneDate = LocalDate.of(2024, 7, 5);
            doReturn(true).when(householdQuery).householdExists(householdId);
            taskService.createTask(householdId, new Task(taskId, "Clean", LocalDate.of(2024, 7, 1)));

            // when
            taskService.updateTask(taskId, new TaskUpdate().done(doneDate), currentUserId);

            // then
            assertThat(taskCompletionRepository.findFirstByTaskIdOrderByDoneDateDesc(taskId))
                    .hasValueSatisfying(c -> {
                        assertThat(c.doneDate()).isEqualTo(doneDate);
                        assertThat(c.doneBy()).isEqualTo(currentUserId);
                    });
        }

        @Test
        void unassignedTask_setsDone_savesCompletionWithCurrentUserInDatabase() {
            // given
            var householdId = UUID.randomUUID();
            var taskId = UUID.randomUUID();
            var currentUserId = UUID.randomUUID();
            var doneDate = LocalDate.of(2024, 7, 5);
            doReturn(true).when(householdQuery).householdExists(householdId);
            taskService.createTask(householdId, new Task(taskId, "Unassigned task", LocalDate.of(2024, 7, 1)));

            // when
            taskService.updateTask(taskId, new TaskUpdate().done(doneDate), currentUserId);

            // then
            assertThat(taskCompletionRepository.findFirstByTaskIdOrderByDoneDateDesc(taskId))
                    .hasValueSatisfying(c -> {
                        assertThat(c.doneDate()).isEqualTo(doneDate);
                        assertThat(c.doneBy()).isEqualTo(currentUserId);
                    });
        }

        @Test
        void recurringTask_advancesDueDateInDatabase() {
            // given
            var householdId = UUID.randomUUID();
            var taskId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var originalDueDate = LocalDate.of(2024, 7, 1);
            var doneDate = LocalDate.of(2024, 7, 3);
            doReturn(true).when(householdQuery).householdExists(householdId);
            taskService.createTask(householdId, new Task(taskId, "Weekly clean", originalDueDate)
                    .assignedTo(memberId)
                    .recurring(true)
                    .recurrenceUnit(Task.RecurrenceUnitEnum.WEEKS)
                    .recurrenceInterval(1));

            // when
            taskService.updateTask(taskId, new TaskUpdate().done(doneDate), memberId);

            // then
            assertThat(taskRepository.findById(taskId))
                    .hasValueSatisfying(e -> assertThat(e.getDueDate()).isEqualTo(originalDueDate.plusWeeks(1)));
        }
    }

    @Nested
    class getTaskStatsByMember {

        @Test
        void recurringTaskCompletedTwiceInPeriod_doneCountIsTwo() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var taskId = UUID.randomUUID();
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);
            doReturn(true).when(householdQuery).householdExists(householdId);
            doReturn(Map.of(memberId, "Alice")).when(memberQuery).findMemberNamesByIds(any());
            taskService.createTask(householdId, new Task(taskId, "Weekly clean", LocalDate.of(2024, 7, 1))
                    .assignedTo(memberId)
                    .recurring(true)
                    .recurrenceUnit(Task.RecurrenceUnitEnum.WEEKS)
                    .recurrenceInterval(1));
            taskService.updateTask(taskId, new TaskUpdate().done(LocalDate.of(2024, 7, 5)), memberId);
            taskService.updateTask(taskId, new TaskUpdate().done(LocalDate.of(2024, 7, 12)), memberId);
            var expected = new TaskStatsByMember(memberId, "Alice", 2, 0);

            // when
            var result = taskService.getTaskStatsByMember(householdId, from, to);

            // then
            assertThat(result).singleElement().usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class onHouseholdDeleted {

        @Test
        void deletedHousehold_removesAllTasksFromDatabase() {
            // given
            var householdId = UUID.randomUUID();
            doReturn(true).when(householdQuery).householdExists(householdId);
            taskService.createTask(householdId, new Task(UUID.randomUUID(), "Task 1", LocalDate.of(2024, 7, 1)));
            taskService.createTask(householdId, new Task(UUID.randomUUID(), "Task 2", LocalDate.of(2024, 7, 1)));

            // when — call repository directly: @ApplicationModuleListener uses REQUIRES_NEW, which
            // cannot see uncommitted test data from the surrounding @DataJdbcTest transaction
            taskRepository.deleteByHouseholdId(householdId);

            // then
            assertThat(taskRepository.findByHouseholdId(householdId)).isEmpty();
        }
    }
}
