package eu.wiegandt.librehousehold.expenses.repository;

import eu.wiegandt.librehousehold.expenses.model.CategoryEntity;
import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;
import org.springframework.stereotype.Component;

@Component
public class CategoryEntityCallbacks implements AfterConvertCallback<CategoryEntity> {

    @Override
    public CategoryEntity onAfterConvert(CategoryEntity entity) {
        entity.markExisting();
        return entity;
    }
}
