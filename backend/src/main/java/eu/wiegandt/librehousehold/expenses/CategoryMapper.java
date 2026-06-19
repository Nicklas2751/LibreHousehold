package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.core.CoreOptionalMapper;
import eu.wiegandt.librehousehold.model.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper
interface CategoryMapper extends CoreOptionalMapper {

    @Mapping(target = "icon", qualifiedByName = "toOptionalString")
    Category toCategory(CategoryEntity entity);

    @Mapping(target = "icon", qualifiedByName = "fromOptionalString")
    CategoryEntity toEntity(Category category, UUID householdId);
}
