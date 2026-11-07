package eu.wiegandt.librehousehold.realtime.service;

import eu.wiegandt.librehousehold.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.realtime.model.Signal;
import eu.wiegandt.librehousehold.realtime.model.SignalType;
import eu.wiegandt.librehousehold.realtime.model.StreamConnection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Writes signals and heartbeats to open streams. The realtime listeners run synchronously in the publishing thread
 * (ADR-019), so every write goes through the executor: a slow client never blocks the publisher, and
 * a broken connection only closes itself, never the others or the publisher (RT12).
 */
@Component
public class SignalSender {

    // Spring Boot's TaskExecutor; the scheduler of @EnableScheduling is a TaskExecutor as well
    private static final String APPLICATION_TASK_EXECUTOR = "applicationTaskExecutor";
    // Spring writes the colon without a space; the leading space yields ": heartbeat" as in api/asyncapi.yml
    private static final String HEARTBEAT_COMMENT = " heartbeat";

    private final TaskExecutor executor;
    private final HouseholdEventStreams streams;

    SignalSender(@Qualifier(APPLICATION_TASK_EXECUTOR) TaskExecutor executor, HouseholdEventStreams streams) {
        this.executor = executor;
        this.streams = streams;
    }

    public void send(SignalType type, Signal signal, Collection<StreamConnection> connections) {
        connections.forEach(connection -> executor.execute(() -> sendTo(connection, () -> signalEvent(type, signal))));
    }

    /**
     * Sends the heartbeat comment of {@code api/asyncapi.yml}. Through the executor as well, so a
     * client that stopped reading never blocks the scheduler thread, which all scheduled jobs share.
     */
    public void sendHeartbeat(Collection<StreamConnection> connections) {
        connections.forEach(connection -> executor.execute(() -> sendTo(connection, SignalSender::heartbeatEvent)));
    }

    // One builder per connection: SseEventBuilder is stateful and consumed by send
    private void sendTo(StreamConnection connection, Supplier<SseEventBuilder> event) {
        try {
            connection.emitter().send(event.get());
        } catch (IOException | IllegalStateException failure) {
            connection.emitter().completeWithError(failure);
            streams.remove(connection, CloseReason.SEND_FAILED);
        }
    }

    private static SseEventBuilder signalEvent(SignalType type, Signal signal) {
        return SseEmitter.event().name(type.wireName()).data(signal, MediaType.APPLICATION_JSON);
    }

    private static SseEventBuilder heartbeatEvent() {
        return SseEmitter.event().comment(HEARTBEAT_COMMENT);
    }
}
