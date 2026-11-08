package eu.wiegandt.librehousehold.notifications.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings of the household event stream (ADR-019). The values and the reasoning behind them live only
 * in {@code librehousehold.notifications.realtime} of {@code application.yaml}.
 *
 * @param reconnectDelay        sent as the SSE {@code retry:} field, the delay before {@code EventSource}
 *                              reconnects
 * @param emitterTimeout        how long a stream stays open before the server ends it and the client
 *                              reconnects; must be shorter than {@code server.servlet.session.timeout}
 * @param maxStreamsPerSession  how many streams one session may keep open at the same time (RT5)
 * @param maxStreamsPerMember   how many streams one member may keep open at the same time, across sessions
 * @param maxStreams            how many streams this instance keeps open at the same time
 * @param retryAfter            sent as {@code Retry-After} when a stream is rejected for one of the limits
 */
@ConfigurationProperties(prefix = "librehousehold.notifications.realtime")
@Validated
@EmitterTimeoutBelowSessionTimeout
public record RealtimeProperties(Duration reconnectDelay, Duration emitterTimeout, int maxStreamsPerSession,
                                 int maxStreamsPerMember, int maxStreams, Duration retryAfter) {
}
