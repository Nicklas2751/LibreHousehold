package eu.wiegandt.librehousehold.tasks;

import eu.wiegandt.librehousehold.model.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Mapper
interface TaskMapper {

    Task toTask(TaskEntity entity);

    @Mapping(source = "householdId", target = "householdId")
    TaskEntity toEntity(Task task, UUID householdId);

    default <T> Optional<T> toOptional(T value) {
        return Optional.ofNullable(value);
    }

    default String fromOptional(Optional<String> value) {
        return value.orElse(null);
    }

    default UUID fromOptionalUUID(Optional<UUID> value) {
        return value.orElse(null);
    }

    default LocalDate fromOptionalDate(Optional<LocalDate> value) {
        return value.orElse(null);
    }

    default Integer fromOptionalInt(Optional<Integer> value) {
        return value.orElse(null);
    }

    default boolean fromOptionalBoolean(Optional<Boolean> value) {
        return value.orElse(false);
    }

    default Optional<Boolean> toOptionalBoolean(boolean value) {
        return Optional.of(value);
    }

    default String fromOptionalRecurrenceUnit(Optional<Task.RecurrenceUnitEnum> value) {
        return value.map(Enum::name).orElse(null);
    }
}
