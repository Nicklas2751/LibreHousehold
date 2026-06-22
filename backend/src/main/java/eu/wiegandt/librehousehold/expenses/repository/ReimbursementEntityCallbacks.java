package eu.wiegandt.librehousehold.expenses.repository;

import eu.wiegandt.librehousehold.expenses.model.ReimbursementEntity;
import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;
import org.springframework.stereotype.Component;

@Component
public class ReimbursementEntityCallbacks implements AfterConvertCallback<ReimbursementEntity> {

    @Override
    public ReimbursementEntity onAfterConvert(ReimbursementEntity entity) {
        entity.markExisting();
        return entity;
    }
}
