package eu.wiegandt.librehousehold.expenses;

import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;
import org.springframework.stereotype.Component;

@Component
class ExpenseEntityCallbacks implements AfterConvertCallback<ExpenseEntity> {

    @Override
    public ExpenseEntity onAfterConvert(ExpenseEntity entity) {
        entity.markExisting();
        return entity;
    }
}
