package eu.wiegandt.librehousehold.core;

import org.mapstruct.Condition;
import org.mapstruct.Named;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * MapStruct mapper mixin that handles {@link java.util.Optional} fields in PATCH-style update mappings.
 * Mappers in any module that need Optional conversion should extend this interface.
 *
 * <p>The {@code isPresent} condition prevents overwriting an existing value when an Optional
 * field is {@code null} in the source (i.e. absent from the PATCH request), which is the
 * expected behaviour for partial updates.
 */
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
