package eu.wiegandt.librehousehold.notifications.realtime.model;

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
    /** The session is no longer registered, e.g. after a logout or its timeout. */
    SESSION_ENDED,
    /** The session was expired, e.g. by a password reset; its next request would be rejected. */
    SESSION_EXPIRED,
    /** The member was removed from the household; the stream got {@code session.terminated} first. */
    MEMBER_REMOVED,
    /** The household was deleted; the stream got {@code household.deleted} and {@code session.terminated} first. */
    HOUSEHOLD_DELETED,
    /** Writing a signal to the connection failed. */
    SEND_FAILED,
    /** The application shuts down. */
    SHUTDOWN
}
