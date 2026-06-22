package eu.wiegandt.librehousehold.expenses.repository;

import eu.wiegandt.librehousehold.expenses.model.ExpenseEntity;
import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;
import org.springframework.stereotype.Component;

@Component
public class ExpenseEntityCallbacks implements AfterConvertCallback<ExpenseEntity> {

    @Override
    public ExpenseEntity onAfterConvert(ExpenseEntity entity) {
        entity.markExisting();
        return entity;
    }
}
