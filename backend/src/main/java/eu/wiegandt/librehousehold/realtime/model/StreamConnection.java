package eu.wiegandt.librehousehold.realtime.model;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * One open event stream. Remembers the session it was opened with, so the stream can be closed
 * when that session ends (ADR-019); the session ID never leaves the server.
 */
public record StreamConnection(UUID householdId, UUID memberId, String sessionId, SseEmitter emitter) {
}
