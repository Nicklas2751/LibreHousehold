package eu.wiegandt.librehousehold.notifications.realtime.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * The kind of entity an {@link InvalidationSignal} refers to, schema {@code Entity} in
 * {@code api/asyncapi.yml}.
 */
public enum SignalEntity {
    TASK,
    EXPENSE,
    REIMBURSEMENT,
    CATEGORY,
    MEMBER,
    HOUSEHOLD;

    @JsonValue
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
