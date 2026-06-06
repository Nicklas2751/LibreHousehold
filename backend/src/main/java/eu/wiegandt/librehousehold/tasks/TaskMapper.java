package eu.wiegandt.librehousehold.tasks;

import eu.wiegandt.librehousehold.model.Task;
import eu.wiegandt.librehousehold.model.TaskEdit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Mapper
interface TaskMapper {

    @Mapping(target = "assignedTo", qualifiedByName = "toOptional")
    @Mapping(target = "description", qualifiedByName = "toOptional")
    @Mapping(target = "recurring", qualifiedByName = "toOptionalBoolean")
    @Mapping(target = "recurrenceUnit", qualifiedByName = "toOptionalTaskRecurrenceUnit")
    @Mapping(target = "recurrenceInterval", qualifiedByName = "toOptional")
    @Mapping(target = "done", qualifiedByName = "toOptional")
    Task toTask(TaskEntity entity);

    @Mapping(source = "householdId", target = "householdId")
    @Mapping(target = "assignedTo", qualifiedByName = "fromOptionalUUID")
    @Mapping(target = "description", qualifiedByName = "fromOptional")
    @Mapping(target = "recurring", qualifiedByName = "fromOptionalBoolean")
    @Mapping(target = "recurrenceUnit", qualifiedByName = "fromOptionalRecurrenceUnit")
    @Mapping(target = "recurrenceInterval", qualifiedByName = "fromOptionalInt")
    @Mapping(target = "done", qualifiedByName = "fromOptionalDate")
    TaskEntity toEntity(Task task, UUID householdId);

    @Mapping(target = "description", qualifiedByName = "fromOptional")
    @Mapping(target = "assignedTo", qualifiedByName = "fromOptionalUUID")
    @Mapping(target = "recurring", qualifiedByName = "fromOptionalBoolean")
    @Mapping(target = "recurrenceUnit", qualifiedByName = "fromOptionalTaskEditRecurrenceUnit")
    @Mapping(target = "recurrenceInterval", qualifiedByName = "fromOptionalInt")
    @Mapping(target = "done", ignore = true)
    @Mapping(target = "householdId", ignore = true)
    void updateEntityFromEdit(TaskEdit edit, @MappingTarget TaskEntity entity);

    @Named("toOptional")
    default <T> Optional<T> toOptional(T value) {
        return Optional.ofNullable(value);
    }

    @Named("toOptionalBoolean")
    default Optional<Boolean> toOptionalBoolean(boolean value) {
        return Optional.of(value);
    }

    @Named("toOptionalTaskRecurrenceUnit")
    default Optional<Task.RecurrenceUnitEnum> toOptionalTaskRecurrenceUnit(String value) {
        return Optional.ofNullable(value).map(Task.RecurrenceUnitEnum::valueOf);
    }

    @Named("fromOptional")
    default String fromOptional(Optional<String> value) {
        return value.orElse(null);
    }

    @Named("fromOptionalUUID")
    default UUID fromOptionalUUID(Optional<UUID> value) {
        return value.orElse(null);
    }

    @Named("fromOptionalDate")
    default LocalDate fromOptionalDate(Optional<LocalDate> value) {
        return value.orElse(null);
    }

    @Named("fromOptionalInt")
    default Integer fromOptionalInt(Optional<Integer> value) {
        return value.orElse(null);
    }

    @Named("fromOptionalBoolean")
    default boolean fromOptionalBoolean(Optional<Boolean> value) {
        return value.orElse(false);
    }

    @Named("fromOptionalRecurrenceUnit")
    default String fromOptionalRecurrenceUnit(Optional<Task.RecurrenceUnitEnum> value) {
        return value.map(Enum::name).orElse(null);
    }

    @Named("fromOptionalTaskEditRecurrenceUnit")
    default String fromOptionalTaskEditRecurrenceUnit(Optional<TaskEdit.RecurrenceUnitEnum> value) {
        return value.map(Enum::name).orElse(null);
    }
}
