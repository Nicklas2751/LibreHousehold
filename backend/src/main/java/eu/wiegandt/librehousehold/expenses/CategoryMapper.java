package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.core.CoreOptionalMapper;
import eu.wiegandt.librehousehold.model.Category;
import eu.wiegandt.librehousehold.model.CategoryUpdate;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.UUID;

@Mapper
interface CategoryMapper extends CoreOptionalMapper {

    @Mapping(target = "icon", qualifiedByName = "toOptionalString")
    Category toCategory(CategoryEntity entity);

    @Mapping(target = "icon", qualifiedByName = "fromOptionalString")
    CategoryEntity toEntity(Category category, UUID householdId);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "householdId", ignore = true)
    @Mapping(target = "name", source = "name",
             conditionQualifiedByName = "isPresent", qualifiedByName = "fromOptionalString")
    @Mapping(target = "icon", source = "icon",
             conditionQualifiedByName = "isPresent", qualifiedByName = "fromOptionalString")
    void updateEntityFromUpdate(CategoryUpdate source, @MappingTarget CategoryEntity target);
}
