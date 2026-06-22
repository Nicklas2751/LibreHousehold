package eu.wiegandt.librehousehold.tasks.repository;

import eu.wiegandt.librehousehold.tasks.model.TaskCompletionEntity;
import eu.wiegandt.librehousehold.tasks.model.TaskEntity;
import org.instancio.Instancio;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration"
})
class TaskCompletionRepositoryIT {

    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Autowired
    private TaskCompletionRepository taskCompletionRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Nested
    class findByHouseholdIdAndPeriod {

        @Test
        void noCompletions_returnsEmpty() {
            // given
            var householdId = Instancio.create(UUID.class);
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);

            // when
            var result = taskCompletionRepository.findByHouseholdIdAndPeriod(householdId, from, to);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void completionWithinPeriod_returned() {
            // given
            var householdId = Instancio.create(UUID.class);
            var memberId = Instancio.create(UUID.class);
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);
            var task = new TaskEntity(Instancio.create(UUID.class), householdId, memberId, "T", null, LocalDate.of(2024, 7, 1), false, null, null);
            taskRepository.save(task);
            taskCompletionRepository.save(new TaskCompletionEntity(Instancio.create(UUID.class), task.getId(), memberId, LocalDate.of(2024, 7, 10)));

            // when
            var result = taskCompletionRepository.findByHouseholdIdAndPeriod(householdId, from, to);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().doneBy()).isEqualTo(memberId);
        }

        @Test
        void completionOutsidePeriod_notReturned() {
            // given
            var householdId = Instancio.create(UUID.class);
            var memberId = Instancio.create(UUID.class);
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);
            var task = new TaskEntity(Instancio.create(UUID.class), householdId, memberId, "T", null, LocalDate.of(2024, 6, 1), false, null, null);
            taskRepository.save(task);
            taskCompletionRepository.save(new TaskCompletionEntity(Instancio.create(UUID.class), task.getId(), memberId, LocalDate.of(2024, 6, 15)));

            // when
            var result = taskCompletionRepository.findByHouseholdIdAndPeriod(householdId, from, to);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void twoCompletionsSameMember_bothReturned() {
            // given
            var householdId = Instancio.create(UUID.class);
            var memberId = Instancio.create(UUID.class);
            var from = LocalDate.of(2024, 7, 1);
            var to = LocalDate.of(2024, 7, 31);
            var task = new TaskEntity(Instancio.create(UUID.class), householdId, memberId, "T", null, LocalDate.of(2024, 7, 1), true, "WEEKS", 1);
            taskRepository.save(task);
            taskCompletionRepository.save(new TaskCompletionEntity(Instancio.create(UUID.class), task.getId(), memberId, LocalDate.of(2024, 7, 5)));
            taskCompletionRepository.save(new TaskCompletionEntity(Instancio.create(UUID.class), task.getId(), memberId, LocalDate.of(2024, 7, 12)));

            // when
            var result = taskCompletionRepository.findByHouseholdIdAndPeriod(householdId, from, to);

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class deleteByTaskIdAndDoneDate {

        @Test
        void existingCompletion_deleted() {
            // given
            var householdId = Instancio.create(UUID.class);
            var memberId = Instancio.create(UUID.class);
            var doneDate = LocalDate.of(2024, 7, 5);
            var task = new TaskEntity(Instancio.create(UUID.class), householdId, memberId, "T", null, LocalDate.of(2024, 7, 1), false, null, null);
            taskRepository.save(task);
            var completionId = Instancio.create(UUID.class);
            taskCompletionRepository.save(new TaskCompletionEntity(completionId, task.getId(), memberId, doneDate));

            // when
            taskCompletionRepository.deleteByTaskIdAndDoneDate(task.getId(), doneDate);

            // then
            assertThat(taskCompletionRepository.findById(completionId)).isEmpty();
        }
    }

    @Nested
    class findFirstByTaskIdOrderByDoneDateDesc {

        @Test
        void noCompletion_returnsEmpty() {
            // given
            var taskId = Instancio.create(UUID.class);

            // when
            var result = taskCompletionRepository.findFirstByTaskIdOrderByDoneDateDesc(taskId);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void oneCompletion_returnsIt() {
            // given
            var householdId = Instancio.create(UUID.class);
            var memberId = Instancio.create(UUID.class);
            var task = new TaskEntity(Instancio.create(UUID.class), householdId, memberId, "T", null, LocalDate.of(2024, 7, 1), false, null, null);
            taskRepository.save(task);
            var completionId = Instancio.create(UUID.class);
            taskCompletionRepository.save(new TaskCompletionEntity(completionId, task.getId(), memberId, LocalDate.of(2024, 7, 5)));

            // when
            var result = taskCompletionRepository.findFirstByTaskIdOrderByDoneDateDesc(task.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(completionId);
        }

        @Test
        void twoCompletions_returnsNewest() {
            // given
            var householdId = Instancio.create(UUID.class);
            var memberId = Instancio.create(UUID.class);
            var task = new TaskEntity(Instancio.create(UUID.class), householdId, memberId, "T", null, LocalDate.of(2024, 7, 1), true, "WEEKS", 1);
            taskRepository.save(task);
            var olderCompletionId = Instancio.create(UUID.class);
            var newerCompletionId = Instancio.create(UUID.class);
            taskCompletionRepository.save(new TaskCompletionEntity(olderCompletionId, task.getId(), memberId, LocalDate.of(2024, 7, 5)));
            taskCompletionRepository.save(new TaskCompletionEntity(newerCompletionId, task.getId(), memberId, LocalDate.of(2024, 7, 12)));

            // when
            var result = taskCompletionRepository.findFirstByTaskIdOrderByDoneDateDesc(task.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(newerCompletionId);
        }
    }

    @Nested
    class findLatestByTaskIdIn {

        @Test
        void twoTasksWithOneCompletionEach_returnsOnePerTask() {
            // given
            var householdId = Instancio.create(UUID.class);
            var memberId = Instancio.create(UUID.class);
            var task1 = new TaskEntity(Instancio.create(UUID.class), householdId, memberId, "T1", null, LocalDate.of(2024, 7, 1), false, null, null);
            var task2 = new TaskEntity(Instancio.create(UUID.class), householdId, memberId, "T2", null, LocalDate.of(2024, 7, 1), false, null, null);
            taskRepository.save(task1);
            taskRepository.save(task2);
            var completion1Id = Instancio.create(UUID.class);
            var completion2Id = Instancio.create(UUID.class);
            taskCompletionRepository.save(new TaskCompletionEntity(completion1Id, task1.getId(), memberId, LocalDate.of(2024, 7, 5)));
            taskCompletionRepository.save(new TaskCompletionEntity(completion2Id, task2.getId(), memberId, LocalDate.of(2024, 7, 6)));

            // when
            var result = taskCompletionRepository.findLatestByTaskIdIn(List.of(task1.getId(), task2.getId()));

            // then
            assertThat(result).hasSize(2);
        }

        @Test
        void oneTaskWithTwoCompletions_returnsOnlyNewest() {
            // given
            var householdId = Instancio.create(UUID.class);
            var memberId = Instancio.create(UUID.class);
            var task = new TaskEntity(Instancio.create(UUID.class), householdId, memberId, "T", null, LocalDate.of(2024, 7, 1), true, "WEEKS", 1);
            taskRepository.save(task);
            var olderCompletionId = Instancio.create(UUID.class);
            var newerCompletionId = Instancio.create(UUID.class);
            taskCompletionRepository.save(new TaskCompletionEntity(olderCompletionId, task.getId(), memberId, LocalDate.of(2024, 7, 5)));
            taskCompletionRepository.save(new TaskCompletionEntity(newerCompletionId, task.getId(), memberId, LocalDate.of(2024, 7, 12)));

            // when
            var result = taskCompletionRepository.findLatestByTaskIdIn(List.of(task.getId()));

            // then
            assertThat(result).singleElement();
            assertThat(result.getFirst().id()).isEqualTo(newerCompletionId);
        }
    }
}
