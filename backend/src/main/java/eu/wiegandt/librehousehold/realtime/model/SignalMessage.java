package eu.wiegandt.librehousehold.realtime.model;

/**
 * One event of the stream: its type, sent as the SSE {@code event:} field, and its payload, sent as
 * {@code data:}. Corresponds to a message of {@code api/asyncapi.yml}.
 */
public record SignalMessage(SignalType type, Signal payload) {
}
