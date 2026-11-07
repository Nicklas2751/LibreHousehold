package eu.wiegandt.librehousehold.realtime.service;

import eu.wiegandt.librehousehold.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.realtime.model.SignalType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer metrics of the household event stream: how many streams are open, why they ended and
 * which signals went out.
 */
@Component
public class StreamMetrics {

    private static final String ACTIVE_STREAMS = "librehousehold.realtime.streams.active";
    private static final String CLOSED_STREAMS = "librehousehold.realtime.streams.closed";
    private static final String SENT_SIGNALS = "librehousehold.realtime.signals.sent";
    private static final String REASON_TAG = "reason";
    private static final String TYPE_TAG = "type";

    private final MeterRegistry registry;
    private final AtomicInteger activeStreams;

    public StreamMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.activeStreams = registry.gauge(ACTIVE_STREAMS, new AtomicInteger());
    }

    public void streamOpened() {
        activeStreams.incrementAndGet();
    }

    public void streamClosed(CloseReason reason) {
        activeStreams.decrementAndGet();
        Counter.builder(CLOSED_STREAMS)
                .tag(REASON_TAG, reason.name().toLowerCase(Locale.ROOT))
                .register(registry)
                .increment();
    }

    public void signalSent(SignalType type) {
        Counter.builder(SENT_SIGNALS)
                .tag(TYPE_TAG, type.wireName())
                .register(registry)
                .increment();
    }
}
