package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.core.CoreOptionalMapper;
import eu.wiegandt.librehousehold.model.Reimbursement;
import eu.wiegandt.librehousehold.model.ReimbursementCreate;
import eu.wiegandt.librehousehold.model.ReimbursementUpdate;
import org.mapstruct.*;

import java.util.Optional;
import java.util.UUID;

@Mapper
interface ReimbursementMapper extends CoreOptionalMapper {

    @Mapping(target = "notes", source = "notes", qualifiedByName = "toOptionalString")
    @Mapping(target = "status", source = "status", qualifiedByName = "toStatusEnum")
    Reimbursement toReimbursement(ReimbursementEntity entity);

    @Mapping(target = "householdId", source = "householdId")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "notes", source = "create.notes", qualifiedByName = "fromOptionalString")
    ReimbursementEntity toEntity(ReimbursementCreate create, UUID id, UUID householdId);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "status", source = "status", conditionQualifiedByName = "isPresent", qualifiedByName = "optionalStatusToString")
    @Mapping(target = "notes", source = "notes", conditionQualifiedByName = "isPresent", qualifiedByName = "fromOptionalString")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "householdId", ignore = true)
    @Mapping(target = "amount", ignore = true)
    @Mapping(target = "creditorId", ignore = true)
    @Mapping(target = "debtorId", ignore = true)
    void updateEntityFromUpdate(ReimbursementUpdate source, @MappingTarget ReimbursementEntity target);

    @Named("optionalStatusToString")
    default String optionalStatusToString(Optional<ReimbursementUpdate.StatusEnum> status) {
        return status == null ? null : status.map(ReimbursementUpdate.StatusEnum::getValue).orElse(null);
    }

    @Named("toStatusEnum")
    default Reimbursement.StatusEnum toStatusEnum(String status) {
        return Reimbursement.StatusEnum.fromValue(status);
    }
}
