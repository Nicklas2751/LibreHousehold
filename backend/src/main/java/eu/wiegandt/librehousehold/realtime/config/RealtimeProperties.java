package eu.wiegandt.librehousehold.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings of the household event stream (ADR-019), see {@code librehousehold.realtime} in
 * {@code application.yaml} for the reasoning behind the defaults.
 *
 * @param reconnectDelay sent as the SSE {@code retry:} field, the delay before {@code EventSource}
 *                       reconnects
 * @param emitterTimeout how long a stream stays open before the server ends it and the client
 *                       reconnects; must be shorter than {@code server.servlet.session.timeout}
 */
@ConfigurationProperties(prefix = "librehousehold.realtime")
@Validated
@EmitterTimeoutBelowSessionTimeout
public record RealtimeProperties(@DefaultValue("5s") Duration reconnectDelay,
                                 @DefaultValue("25m") Duration emitterTimeout) {
}
