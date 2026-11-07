package eu.wiegandt.librehousehold.realtime.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload of every change event, schema {@code InvalidationSignal} in {@code api/asyncapi.yml}.
 * Deliberately carries no business data: the client reloads the entity through the REST API, so
 * authorization stays there (ADR-019).
 */
public record InvalidationSignal(SignalEntity entity, UUID entityId, Instant occurredAt) implements Signal {
}
