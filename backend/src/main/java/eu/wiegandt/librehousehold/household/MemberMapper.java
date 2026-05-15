package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.Member;
import org.mapstruct.Mapper;

import java.util.Optional;

@Mapper
interface MemberMapper {

    default String fromOptional(Optional<String> value) {
        return value.orElse(null);
    }

    default Optional<String> toOptional(String value) {
        return Optional.ofNullable(value);
    }

    MemberEntity toMemberEntity(Member member);
}
