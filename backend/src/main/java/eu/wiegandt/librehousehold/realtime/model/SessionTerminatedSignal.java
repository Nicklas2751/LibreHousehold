package eu.wiegandt.librehousehold.realtime.model;

import java.time.Instant;

/**
 * Payload of {@code session.terminated}, schema {@code SessionTerminatedSignal} in
 * {@code api/asyncapi.yml}.
 */
public record SessionTerminatedSignal(Instant occurredAt) implements Signal {
}
