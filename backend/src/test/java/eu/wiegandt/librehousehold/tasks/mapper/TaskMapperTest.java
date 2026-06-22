package eu.wiegandt.librehousehold.tasks.mapper;
import eu.wiegandt.librehousehold.tasks.model.*;
import eu.wiegandt.librehousehold.tasks.mapper.*;

import eu.wiegandt.librehousehold.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskMapperTest {

    private TaskMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(TaskMapper.class);
    }

    @Nested
    class toTask {

        @Test
        void entityWithAllFields_mapsAllFieldsCorrectly() {
            // given
            var entity = new TaskEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Clean kitchen",
                    "Wipe surfaces", LocalDate.of(2024, 7, 1), LocalDate.of(2024, 6, 30), true, "WEEKS", 2);
            var expected = new Task(entity.getId(), "Clean kitchen", LocalDate.of(2024, 7, 1))
                    .assignedTo(entity.getAssignedTo())
                    .description("Wipe surfaces")
                    .done(LocalDate.of(2024, 6, 30))
                    .recurring(true)
                    .recurrenceUnit(Task.RecurrenceUnitEnum.WEEKS)
                    .recurrenceInterval(2);

            // when
            var task = mapper.toTask(entity);

            // then
            assertThat(task).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void entityWithNullOptionalFields_returnsTaskWithEmptyOptionals() {
            // given
            var entity = new TaskEntity(UUID.randomUUID(), UUID.randomUUID(), null, "Title",
                    null, LocalDate.of(2024, 7, 1), null, false, null, null);
            var expected = new Task(entity.getId(), "Title", LocalDate.of(2024, 7, 1))
                    .recurring(false);

            // when
            var task = mapper.toTask(entity);

            // then
            assertThat(task).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void nullEntity_returnsNull() {
            // when / then
            assertThat(mapper.toTask(null)).isNull();
        }

        @ParameterizedTest
        @EnumSource(Task.RecurrenceUnitEnum.class)
        void entityWithRecurrenceUnit_mapsEnumValueCorrectly(Task.RecurrenceUnitEnum recurrenceUnit) {
            // given
            var entity = new TaskEntity(UUID.randomUUID(), UUID.randomUUID(), null, "T",
                    null, LocalDate.of(2024, 7, 1), null, true, recurrenceUnit.name(), 1);
            var expected = new Task(entity.getId(), "T", LocalDate.of(2024, 7, 1))
                    .recurring(true)
                    .recurrenceUnit(recurrenceUnit)
                    .recurrenceInterval(1);

            // when / then
            assertThat(mapper.toTask(entity)).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class toEntity {

        @Test
        void taskWithAllFields_mapsAllFieldsCorrectly() {
            // given
            var householdId = UUID.randomUUID();
            var task = new Task(UUID.randomUUID(), "Take out trash", LocalDate.of(2024, 7, 1))
                    .assignedTo(UUID.randomUUID())
                    .description("Put in bin")
                    .recurring(true)
                    .recurrenceUnit(Task.RecurrenceUnitEnum.WEEKS)
                    .recurrenceInterval(2)
                    .done(LocalDate.of(2024, 6, 30));
            var expected = new TaskEntity(task.getId(), householdId, task.getAssignedTo().orElseThrow(),
                    "Take out trash", "Put in bin", LocalDate.of(2024, 7, 1),
                    LocalDate.of(2024, 6, 30), true, "WEEKS", 2);

            // when
            var entity = mapper.toEntity(task, householdId);

            // then
            assertThat(entity).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void taskWithEmptyOptionalFields_mapsToNullEntityFields() {
            // given
            var householdId = UUID.randomUUID();
            var task = new Task(UUID.randomUUID(), "Simple task", LocalDate.of(2024, 7, 1));
            var expected = new TaskEntity(task.getId(), householdId, null, "Simple task",
                    null, LocalDate.of(2024, 7, 1), null, false, null, null);

            // when
            var entity = mapper.toEntity(task, householdId);

            // then
            assertThat(entity).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void nullTaskAndHouseholdId_returnsNull() {
            // when / then
            assertThat(mapper.toEntity(null, null)).isNull();
        }

        @ParameterizedTest
        @EnumSource(Task.RecurrenceUnitEnum.class)
        void taskWithRecurrenceUnit_mapsStringCorrectly(Task.RecurrenceUnitEnum recurrenceUnit) {
            // given
            var householdId = UUID.randomUUID();
            var task = new Task(UUID.randomUUID(), "Task", LocalDate.of(2024, 7, 1))
                    .recurrenceUnit(recurrenceUnit);
            var expected = new TaskEntity(task.getId(), householdId, null, "Task",
                    null, LocalDate.of(2024, 7, 1), null, false, recurrenceUnit.name(), null);

            // when
            var entity = mapper.toEntity(task, householdId);

            // then
            assertThat(entity).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class toOptional {

        @Test
        void nonNullValue_returnsOptionalOfValue() {
            assertThat(mapper.toOptional("hello")).contains("hello");
        }

        @Test
        void nullValue_returnsEmptyOptional() {
            assertThat(mapper.<String>toOptional(null)).isEmpty();
        }
    }

    @Nested
    class fromOptional {

        @Test
        void presentOptional_returnsValue() {
            assertThat(mapper.fromOptional(Optional.of("hello"))).isEqualTo("hello");
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.fromOptional(Optional.<String>empty())).isNull();
        }
    }

    @Nested
    class fromOptionalUUID {

        @Test
        void presentOptional_returnsUUID() {
            var id = UUID.randomUUID();
            assertThat(mapper.fromOptionalUUID(Optional.of(id))).isEqualTo(id);
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.fromOptionalUUID(Optional.empty())).isNull();
        }
    }

    @Nested
    class fromOptionalDate {

        @Test
        void presentOptional_returnsDate() {
            var date = LocalDate.of(2024, 7, 1);
            assertThat(mapper.fromOptionalDate(Optional.of(date))).isEqualTo(date);
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.fromOptionalDate(Optional.empty())).isNull();
        }
    }

    @Nested
    class fromOptionalInt {

        @Test
        void presentOptional_returnsInteger() {
            assertThat(mapper.fromOptionalInt(Optional.of(42))).isEqualTo(42);
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.fromOptionalInt(Optional.empty())).isNull();
        }
    }

    @Nested
    class fromOptionalBoolean {

        @Test
        void presentOptional_returnsValue() {
            assertThat(mapper.fromOptionalBoolean(Optional.of(true))).isTrue();
        }

        @Test
        void emptyOptional_returnsFalse() {
            assertThat(mapper.fromOptionalBoolean(Optional.empty())).isFalse();
        }
    }

    @Nested
    class toOptionalBoolean {

        @Test
        void trueValue_returnsOptionalTrue() {
            assertThat(mapper.toOptionalBoolean(true)).contains(true);
        }

        @Test
        void falseValue_returnsOptionalFalse() {
            assertThat(mapper.toOptionalBoolean(false)).contains(false);
        }
    }

    @Nested
    class fromOptionalRecurrenceUnit {

        @Test
        void presentOptional_returnsEnumName() {
            assertThat(mapper.fromOptionalRecurrenceUnit(Optional.of(Task.RecurrenceUnitEnum.WEEKS))).isEqualTo("WEEKS");
        }

        @Test
        void emptyOptional_returnsNull() {
            assertThat(mapper.fromOptionalRecurrenceUnit(Optional.empty())).isNull();
        }
    }
}