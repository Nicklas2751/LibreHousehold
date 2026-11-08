package eu.wiegandt.librehousehold.notifications.realtime.model;

/**
 * A payload that may go onto the stream. Sealed, so only the contract types of
 * {@code api/asyncapi.yml} can be sent, never an arbitrary object with user text (RT6).
 */
public sealed interface Signal permits InvalidationSignal, SessionTerminatedSignal {
}
