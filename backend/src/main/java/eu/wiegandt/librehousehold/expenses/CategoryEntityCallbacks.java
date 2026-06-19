package eu.wiegandt.librehousehold.expenses;

import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;
import org.springframework.stereotype.Component;

@Component
class CategoryEntityCallbacks implements AfterConvertCallback<CategoryEntity> {

    @Override
    public CategoryEntity onAfterConvert(CategoryEntity entity) {
        entity.markExisting();
        return entity;
    }
}
