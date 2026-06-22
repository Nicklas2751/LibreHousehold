package eu.wiegandt.librehousehold.tasks.repository;

import eu.wiegandt.librehousehold.tasks.model.TaskEntity;
import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Spring Data JDBC entity callback that marks a {@link TaskEntity} as existing after it has been
 * loaded from the database.
 *
 * <p>Because {@link TaskEntity} uses a client-generated UUID as its primary key,
 * {@code isNew()} cannot rely on a {@code null}-check. Instead, it defaults to {@code true}
 * (triggering an {@code INSERT} on {@code save()}). This callback fires after every database read
 * and flips the flag to {@code false}, so that subsequent {@code save()} calls produce an
 * {@code UPDATE} instead.
 *
 * @see <a href="https://docs.spring.io/spring-data/relational/reference/jdbc/entity-callbacks.html">
 *     Spring Data JDBC – Entity Callbacks</a>
 */
@Component
public class TaskEntityCallbacks implements AfterConvertCallback<TaskEntity> {

    @Override
    public TaskEntity onAfterConvert(TaskEntity entity) {
        entity.markExisting();
        return entity;
    }
}