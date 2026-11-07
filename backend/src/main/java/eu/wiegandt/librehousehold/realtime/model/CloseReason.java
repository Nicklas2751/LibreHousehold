package eu.wiegandt.librehousehold.realtime.model;

/**
 * Why an event stream ended.
 */
public enum CloseReason {
    /** The stream completed, e.g. after the client went away or the server completed it. */
    COMPLETED,
    /** The emitter timeout elapsed; {@code EventSource} reconnects on its own. */
    TIMEOUT,
    /** The servlet container reported an error on the connection. */
    ERROR,
    /** Writing a signal to the connection failed. */
    SEND_FAILED,
    /** The application shuts down. */
    SHUTDOWN
}
