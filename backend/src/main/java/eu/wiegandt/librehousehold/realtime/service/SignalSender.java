package eu.wiegandt.librehousehold.realtime.service;

import eu.wiegandt.librehousehold.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.realtime.model.Signal;
import eu.wiegandt.librehousehold.realtime.model.SignalMessage;
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
import java.util.List;
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
     * Sends the messages in the given order and completes the stream afterwards. One executor task per
     * connection: the tasks of a thread pool may run in any order, and the contract of
     * {@code api/asyncapi.yml} fixes the order of the events before the stream ends. The connection
     * leaves the registry right away, so no heartbeat or later signal is written in between.
     */
    public void sendAndClose(List<SignalMessage> messages, Collection<StreamConnection> connections, CloseReason reason) {
        connections.forEach(connection -> close(connection, messages, reason));
    }

    /**
     * Sends the heartbeat comment of {@code api/asyncapi.yml}. Through the executor as well, so a
     * client that stopped reading never blocks the scheduler thread, which all scheduled jobs share.
     */
    public void sendHeartbeat(Collection<StreamConnection> connections) {
        connections.forEach(connection -> executor.execute(() -> sendTo(connection, SignalSender::heartbeatEvent)));
    }

    private void close(StreamConnection connection, List<SignalMessage> messages, CloseReason reason) {
        streams.remove(connection, reason);
        executor.execute(() -> sendAllThenComplete(connection, messages));
    }

    // A failed send already completed the emitter with the error, so the remaining messages are dropped
    private void sendAllThenComplete(StreamConnection connection, List<SignalMessage> messages) {
        for (var message : messages) {
            if (!sendTo(connection, () -> signalEvent(message.type(), message.payload()))) {
                return;
            }
        }
        connection.emitter().complete();
    }

    // One builder per connection: SseEventBuilder is stateful and consumed by send
    private boolean sendTo(StreamConnection connection, Supplier<SseEventBuilder> event) {
        try {
            connection.emitter().send(event.get());
            return true;
        } catch (IOException | IllegalStateException failure) {
            connection.emitter().completeWithError(failure);
            streams.remove(connection, CloseReason.SEND_FAILED);
            return false;
        }
    }

    private static SseEventBuilder signalEvent(SignalType type, Signal signal) {
        return SseEmitter.event().name(type.wireName()).data(signal, MediaType.APPLICATION_JSON);
    }

    private static SseEventBuilder heartbeatEvent() {
        return SseEmitter.event().comment(HEARTBEAT_COMMENT);
    }
}
