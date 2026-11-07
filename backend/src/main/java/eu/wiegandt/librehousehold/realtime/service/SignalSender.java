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

import java.io.IOException;
import java.util.Collection;

/**
 * Sends a signal to open streams. The realtime listeners run synchronously in the publishing thread
 * (ADR-019), so every write goes through the executor: a slow client never blocks the publisher, and
 * a broken connection only closes itself, never the others or the publisher (RT12).
 */
@Component
public class SignalSender {

    // Spring Boot's TaskExecutor; the scheduler of @EnableScheduling is a TaskExecutor as well
    private static final String APPLICATION_TASK_EXECUTOR = "applicationTaskExecutor";

    private final TaskExecutor executor;
    private final HouseholdEventStreams streams;

    SignalSender(@Qualifier(APPLICATION_TASK_EXECUTOR) TaskExecutor executor, HouseholdEventStreams streams) {
        this.executor = executor;
        this.streams = streams;
    }

    public void send(SignalType type, Signal signal, Collection<StreamConnection> connections) {
        connections.forEach(connection -> executor.execute(() -> sendTo(connection, type, signal)));
    }

    private void sendTo(StreamConnection connection, SignalType type, Signal signal) {
        try {
            // One builder per connection: SseEventBuilder is stateful and consumed by send
            connection.emitter().send(SseEmitter.event().name(type.wireName()).data(signal, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException failure) {
            connection.emitter().completeWithError(failure);
            streams.remove(connection, CloseReason.SEND_FAILED);
        }
    }
}
