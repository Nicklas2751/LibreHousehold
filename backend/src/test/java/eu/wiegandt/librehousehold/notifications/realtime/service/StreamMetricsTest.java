package eu.wiegandt.librehousehold.notifications.realtime.service;

import eu.wiegandt.librehousehold.notifications.realtime.model.CloseReason;
import eu.wiegandt.librehousehold.notifications.realtime.model.SignalType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamMetricsTest {

    private static final String ACTIVE_STREAMS = "librehousehold.notifications.realtime.streams.active";
    private static final String CLOSED_STREAMS = "librehousehold.notifications.realtime.streams.closed";
    private static final String SENT_SIGNALS = "librehousehold.notifications.realtime.signals.sent";

    @Nested
    class streamOpened {

        @Test
        void twoStreams_activeGaugeTwo() {
            // given
            var registry = new SimpleMeterRegistry();
            var metrics = new StreamMetrics(registry);

            // when
            metrics.streamOpened();
            metrics.streamOpened();

            // then
            assertThat(registry.get(ACTIVE_STREAMS).gauge().value()).isEqualTo(2);
        }
    }

    @Nested
    class streamClosed {

        @Test
        void openedStream_activeGaugeZero() {
            // given
            var registry = new SimpleMeterRegistry();
            var metrics = new StreamMetrics(registry);
            metrics.streamOpened();

            // when
            metrics.streamClosed(CloseReason.COMPLETED);

            // then
            assertThat(registry.get(ACTIVE_STREAMS).gauge().value()).isZero();
        }

        @Test
        void twoReasons_countedPerReason() {
            // given
            var registry = new SimpleMeterRegistry();
            var metrics = new StreamMetrics(registry);

            // when
            metrics.streamClosed(CloseReason.TIMEOUT);
            metrics.streamClosed(CloseReason.TIMEOUT);
            metrics.streamClosed(CloseReason.MEMBER_REMOVED);

            // then
            assertThat(new double[]{
                    registry.get(CLOSED_STREAMS).tag("reason", "timeout").counter().count(),
                    registry.get(CLOSED_STREAMS).tag("reason", "member_removed").counter().count()})
                    .containsExactly(2, 1);
        }
    }

    @Nested
    class signalSent {

        @Test
        void twoTypes_countedPerWireName() {
            // given
            var registry = new SimpleMeterRegistry();
            var metrics = new StreamMetrics(registry);

            // when
            metrics.signalSent(SignalType.HOUSEHOLD_DELETED);
            metrics.signalSent(SignalType.SESSION_TERMINATED);
            metrics.signalSent(SignalType.SESSION_TERMINATED);

            // then
            assertThat(new double[]{
                    registry.get(SENT_SIGNALS).tag("type", "household.deleted").counter().count(),
                    registry.get(SENT_SIGNALS).tag("type", "session.terminated").counter().count()})
                    .containsExactly(1, 2);
        }
    }
}
