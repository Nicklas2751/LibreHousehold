package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.Household;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Optional;

@Mapper
interface HouseholdSetupMapper {

    default String fromOptional(Optional<String> value) {
        return value.orElse(null);
    }

    default Optional<String> toOptional(String value) {
        return Optional.ofNullable(value);
    }

    @Mapping(target = "adminId", source = "admin")
    HouseholdEntity toHouseholdEntity(Household household);

    @Mapping(target = "admin", source = "adminId")
    Household toApiModel(HouseholdEntity entity);
}
