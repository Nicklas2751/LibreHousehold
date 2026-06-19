package eu.wiegandt.librehousehold.expenses;

import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;
import org.springframework.stereotype.Component;

@Component
class ReimbursementEntityCallbacks implements AfterConvertCallback<ReimbursementEntity> {

    @Override
    public ReimbursementEntity onAfterConvert(ReimbursementEntity entity) {
        entity.markExisting();
        return entity;
    }
}
