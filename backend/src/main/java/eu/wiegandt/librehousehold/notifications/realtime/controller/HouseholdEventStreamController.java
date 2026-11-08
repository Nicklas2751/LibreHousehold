package eu.wiegandt.librehousehold.notifications.realtime.controller;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.notifications.realtime.config.RealtimeProperties;
import eu.wiegandt.librehousehold.notifications.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.notifications.realtime.model.StreamConnection;
import eu.wiegandt.librehousehold.notifications.realtime.service.HouseholdEventStreams;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

/**
 * Opens the event stream of a household (ADR-019). Handwritten instead of generated: the endpoint
 * is described in {@code api/asyncapi.yml}, deliberately not in {@code openapi.yml}.
 */
@RestController
@RequestMapping("${openapi.libreHousehold.base-path:/v1}")
@EnableConfigurationProperties(RealtimeProperties.class)
class HouseholdEventStreamController {

    static final String STREAM_PATH = "/household/{householdId}/events";

    // Keeps Nginx from buffering the stream, which would hold back every event (RT9)
    private static final String ACCEL_BUFFERING_HEADER = "X-Accel-Buffering";
    private static final String ACCEL_BUFFERING_OFF = "no";

    private final HouseholdEventStreams streams;
    private final RealtimeProperties properties;

    HouseholdEventStreamController(HouseholdEventStreams streams, RealtimeProperties properties) {
        this.streams = streams;
        this.properties = properties;
    }

    /**
     * The membership check runs before the emitter exists, so a non-member never gets a byte of the
     * stream (RT1). The stream limits are checked after it and before the first byte, so a non-member
     * cannot use up capacity, and a rejected stream gets a plain 429 (RT5). Reads the session ID but
     * writes nothing into the session, so opening a stream has no side effects (RT4, RT8).
     */
    @GetMapping(path = STREAM_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@householdAccessGuard.isMember(#householdId, authentication)")
    ResponseEntity<SseEmitter> openStream(@PathVariable UUID householdId,
                                          @AuthenticationPrincipal AccountPrincipal principal,
                                          HttpServletRequest request) throws IOException {
        var emitter = new SseEmitter(properties.emitterTimeout().toMillis());
        var connection = new StreamConnection(householdId, principal.memberId(), request.getSession(false).getId(),
                emitter);
        emitter.onCompletion(() -> streams.remove(connection, CloseReason.COMPLETED));
        emitter.onTimeout(() -> closeAfterTimeout(connection));
        emitter.onError(error -> streams.remove(connection, CloseReason.ERROR));
        streams.register(connection);
        emitter.send(SseEmitter.event().reconnectTime(properties.reconnectDelay().toMillis()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(ACCEL_BUFFERING_HEADER, ACCEL_BUFFERING_OFF)
                .body(emitter);
    }

    // Completing ends the response normally; otherwise Spring MVC would treat the timeout as an error
    private void closeAfterTimeout(StreamConnection connection) {
        streams.remove(connection, CloseReason.TIMEOUT);
        connection.emitter().complete();
    }
}
