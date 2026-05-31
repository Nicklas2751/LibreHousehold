package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.Household;
import org.mapstruct.Mapper;

import java.util.Optional;

@Mapper
interface HouseholdSetupMapper {

    default String fromOptional(Optional<String> value) {
        return value.orElse(null);
    }

    default Optional<String> toOptional(String value) {
        return Optional.ofNullable(value);
    }

    HouseholdEntity toHouseholdEntity(Household household);

    Household toApiModel(HouseholdEntity entity);
}
