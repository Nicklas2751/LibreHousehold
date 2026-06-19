package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.core.CoreOptionalMapper;
import eu.wiegandt.librehousehold.model.Expense;
import eu.wiegandt.librehousehold.model.ExpenseUpdate;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper
interface ExpenseMapper extends CoreOptionalMapper {

    @Mapping(target = "notes", qualifiedByName = "toOptionalString")
    @Mapping(target = "isMutable", source = "isMutable", qualifiedByName = "toOptionalBoolean")
    @Mapping(target = "splitBetween", qualifiedByName = "toUuidList")
    Expense toExpense(ExpenseEntity entity, boolean isMutable);

    @Mapping(target = "householdId", source = "householdId")
    @Mapping(target = "notes", qualifiedByName = "fromOptionalString")
    @Mapping(target = "splitBetween", qualifiedByName = "toExpenseSplitRefSet")
    ExpenseEntity toEntity(Expense expense, UUID householdId);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "title", conditionQualifiedByName = "isPresent", qualifiedByName = "fromOptionalString")
    @Mapping(target = "amount", conditionQualifiedByName = "isPresent", qualifiedByName = "optionalDoubleToDecimal")
    @Mapping(target = "paidBy", conditionQualifiedByName = "isPresent", qualifiedByName = "fromOptionalUuid")
    @Mapping(target = "date", conditionQualifiedByName = "isPresent", qualifiedByName = "fromOptionalLocalDate")
    @Mapping(target = "categoryId", conditionQualifiedByName = "isPresent", qualifiedByName = "fromOptionalUuid")
    @Mapping(target = "notes", conditionQualifiedByName = "isPresent", qualifiedByName = "fromOptionalString")
    @Mapping(target = "splitBetween", qualifiedByName = "toExpenseSplitRefSet")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "householdId", ignore = true)
    void updateEntityFromUpdate(ExpenseUpdate source, @MappingTarget ExpenseEntity target);

    @Named("optionalDoubleToDecimal")
    default BigDecimal optionalDoubleToDecimal(Optional<Double> amount) {
        return amount == null ? null : amount.map(BigDecimal::valueOf).orElse(null);
    }

    @Named("toUuidList")
    default List<UUID> toUuidList(Set<ExpenseSplitRef> refs) {
        if (refs == null) return List.of();
        return refs.stream().map(ExpenseSplitRef::memberId).collect(Collectors.toList());
    }

    @Named("toExpenseSplitRefSet")
    default Set<ExpenseSplitRef> toExpenseSplitRefSet(List<UUID> ids) {
        if (ids == null) return Set.of();
        return ids.stream().map(ExpenseSplitRef::new).collect(Collectors.toSet());
    }
}
