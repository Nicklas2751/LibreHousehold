package eu.wiegandt.librehousehold.household.mapper;

import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.model.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Optional;

@Mapper
public interface MemberMapper {

    default String fromOptional(Optional<String> value) {
        return value.orElse(null);
    }

    default Optional<String> toOptional(String value) {
        return Optional.ofNullable(value);
    }

    default Optional<Boolean> toOptionalBoolean(boolean value) {
        return Optional.of(value);
    }

    // Email is stored on AccountEntity, not MemberEntity — populated by the service layer if needed
    @Mapping(target = "email", ignore = true)
    Member toMember(MemberEntity entity);
}
