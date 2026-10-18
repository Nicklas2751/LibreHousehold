package eu.wiegandt.librehousehold.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Resubmission and cleanup settings for the Spring Modulith event publication registry (see
 * {@link EventPublicationMaintenanceJob}).
 */
@ConfigurationProperties(prefix = "librehousehold.events")
public record EventPublicationMaintenanceProperties(Duration resubmitMinAge, int maxAttempts,
                                                    Duration completedRetention) {
}
