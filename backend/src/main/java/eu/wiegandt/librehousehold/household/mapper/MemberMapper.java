package eu.wiegandt.librehousehold.household.mapper;

import eu.wiegandt.librehousehold.core.CoreOptionalMapper;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.model.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper
public interface MemberMapper extends CoreOptionalMapper {

    @Mapping(target = "avatar", source = "avatar", qualifiedByName = "toOptionalString")
    @Mapping(target = "isAdmin", source = "isAdmin", qualifiedByName = "toOptionalBoolean")
    Member toMember(MemberEntity entity);

    @Mapping(target = "avatar", source = "member.avatar", qualifiedByName = "fromOptionalString")
    @Mapping(target = "isAdmin", source = "isAdmin")
    MemberEntity toMemberEntity(Member member, UUID householdId, boolean isAdmin);
}
