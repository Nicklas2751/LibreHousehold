package eu.wiegandt.librehousehold.tasks;

import eu.wiegandt.librehousehold.household.HouseholdDeleted;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.Task;
import eu.wiegandt.librehousehold.model.TaskUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TaskMapperImpl.class, TaskService.class})
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration/tasks"
})
class TaskServiceIT {

    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @MockitoBean
    private HouseholdQuery householdQuery;

    @MockitoBean
    private MemberQuery memberQuery;

    private UUID householdId;

    @BeforeEach
    void setUp() {
        householdId = UUID.randomUUID();
        doReturn(true).when(householdQuery).householdExists(householdId);
    }

    @Nested
    class createTask {

        @Test
        void validTask_persistedInDatabase() {
            // given
            var taskId = UUID.randomUUID();
            var task = new Task(taskId, "Take out trash", LocalDate.of(2024, 7, 1));

            // when
            taskService.createTask(householdId, task);

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
        void nonRecurringTask_setsDoneInDatabase() {
            // given
            var taskId = UUID.randomUUID();
            var doneDate = LocalDate.of(2024, 7, 5);
            taskService.createTask(householdId, new Task(taskId, "Clean", LocalDate.of(2024, 7, 1)));

            // when
            taskService.updateTask(taskId, new TaskUpdate().done(doneDate));

            // then
            assertThat(taskRepository.findById(taskId))
                    .hasValueSatisfying(e -> assertThat(e.getDone()).isEqualTo(doneDate));
        }

        @Test
        void recurringTask_advancesDueDateInDatabase() {
            // given
            var taskId = UUID.randomUUID();
            var originalDueDate = LocalDate.of(2024, 7, 1);
            var doneDate = LocalDate.of(2024, 7, 3);
            var task = new Task(taskId, "Weekly clean", originalDueDate)
                    .recurring(true)
                    .recurrenceUnit(Task.RecurrenceUnitEnum.WEEKS)
                    .recurrenceInterval(1);
            taskService.createTask(householdId, task);

            // when
            taskService.updateTask(taskId, new TaskUpdate().done(doneDate));

            // then
            assertThat(taskRepository.findById(taskId))
                    .hasValueSatisfying(e -> {
                        assertThat(e.getDone()).isEqualTo(doneDate);
                        assertThat(e.getDueDate()).isEqualTo(originalDueDate.plusWeeks(1));
                    });
        }
    }

    @Nested
    class onHouseholdDeleted {

        @Test
        void deletedHousehold_removesAllTasksFromDatabase() {
            // given
            taskService.createTask(householdId, new Task(UUID.randomUUID(), "Task 1", LocalDate.of(2024, 7, 1)));
            taskService.createTask(householdId, new Task(UUID.randomUUID(), "Task 2", LocalDate.of(2024, 7, 1)));

            // when
            taskService.onHouseholdDeleted(new HouseholdDeleted(householdId));

            // then
            assertThat(taskRepository.findByHouseholdId(householdId)).isEmpty();
        }
    }
}