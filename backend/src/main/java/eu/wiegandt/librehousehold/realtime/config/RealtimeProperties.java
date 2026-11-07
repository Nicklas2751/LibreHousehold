package eu.wiegandt.librehousehold.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings of the household event stream (ADR-019), see {@code librehousehold.realtime} in
 * {@code application.yaml} for the reasoning behind the defaults.
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
@ConfigurationProperties(prefix = "librehousehold.realtime")
@Validated
@EmitterTimeoutBelowSessionTimeout
public record RealtimeProperties(@DefaultValue("5s") Duration reconnectDelay,
                                 @DefaultValue("25m") Duration emitterTimeout,
                                 @DefaultValue("5") int maxStreamsPerSession,
                                 @DefaultValue("10") int maxStreamsPerMember,
                                 @DefaultValue("500") int maxStreams,
                                 @DefaultValue("30s") Duration retryAfter) {
}
