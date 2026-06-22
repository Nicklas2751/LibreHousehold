package eu.wiegandt.librehousehold.household.mapper;

import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.model.Household;
import org.mapstruct.Mapper;

import java.util.Optional;

@Mapper
public interface HouseholdSetupMapper {

    default String fromOptional(Optional<String> value) {
        return value.orElse(null);
    }

    default Optional<String> toOptional(String value) {
        return Optional.ofNullable(value);
    }

    HouseholdEntity toHouseholdEntity(Household household);

    Household toApiModel(HouseholdEntity entity);
}
