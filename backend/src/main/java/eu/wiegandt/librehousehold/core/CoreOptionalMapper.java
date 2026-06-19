package eu.wiegandt.librehousehold.core;

import org.mapstruct.Condition;
import org.mapstruct.Named;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("OptionalAssignedToNull")
public interface CoreOptionalMapper {

    @Named("toOptionalString")
    default Optional<String> toOptionalString(String value) {
        return Optional.ofNullable(value);
    }

    @Named("fromOptionalString")
    default String fromOptionalString(Optional<String> value) {
        return value == null ? null : value.orElse(null);
    }

    @Named("fromOptionalUuid")
    default UUID fromOptionalUuid(Optional<UUID> value) {
        return value == null ? null : value.orElse(null);
    }

    @Named("fromOptionalLocalDate")
    default LocalDate fromOptionalLocalDate(Optional<LocalDate> value) {
        return value == null ? null : value.orElse(null);
    }

    @Named("toOptionalBoolean")
    default Optional<Boolean> toOptionalBoolean(boolean value) {
        return Optional.of(value);
    }

    @Condition
    @Named("isPresent")
    default boolean isPresent(Optional<?> optional) {
        return optional != null && optional.isPresent();
    }
}
