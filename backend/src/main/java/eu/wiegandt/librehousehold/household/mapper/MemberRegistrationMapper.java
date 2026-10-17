package eu.wiegandt.librehousehold.household.mapper;

import eu.wiegandt.librehousehold.core.CoreOptionalMapper;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.model.MemberRegistration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper
public interface MemberRegistrationMapper extends CoreOptionalMapper {

    @Mapping(target = "avatar", source = "registration.avatar", qualifiedByName = "fromOptionalString")
    MemberEntity toMemberEntity(MemberRegistration registration, UUID householdId, boolean isAdmin);

    /**
     * Maps a member registered via social login: the e-mail address comes from the external
     * identity the provider verified, never from the request.
     */
    @Mapping(target = "avatar", source = "registration.avatar", qualifiedByName = "fromOptionalString")
    @Mapping(target = "isAdmin", source = "isAdmin")
    @Mapping(target = "email", source = "email")
    MemberEntity toMemberEntityWithEmail(MemberRegistration registration, UUID householdId, boolean isAdmin, String email);
}
