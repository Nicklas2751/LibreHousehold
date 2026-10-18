package eu.wiegandt.librehousehold.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries event publications whose listener failed (e.g. SMTP unreachable) and removes completed
 * publications once they are no longer useful for diagnosis (see "Event delivery and resubmission"
 * in Arc42 Chapter 8).
 *
 * <p>Only {@code FAILED} publications are resubmitted. Publications stuck in {@code PUBLISHED},
 * {@code PROCESSING} or {@code RESUBMITTED} (e.g. after a crash) become {@code FAILED} through
 * Spring Modulith's own staleness monitor ({@code spring.modulith.events.staleness.*}).
 */
@Component
@EnableConfigurationProperties(EventPublicationMaintenanceProperties.class)
public class EventPublicationMaintenanceJob {

    private static final String RUN_INTERVAL = "${librehousehold.events.run-interval}";

    private final FailedEventPublications failedEventPublications;
    private final CompletedEventPublications completedEventPublications;
    private final EventPublicationMaintenanceProperties properties;

    public EventPublicationMaintenanceJob(FailedEventPublications failedEventPublications,
                                          CompletedEventPublications completedEventPublications,
                                          EventPublicationMaintenanceProperties properties) {
        this.failedEventPublications = failedEventPublications;
        this.completedEventPublications = completedEventPublications;
        this.properties = properties;
    }

    // The initial delay keeps the first run out of application startup: publications that just
    // failed get the full interval before they are retried.
    @Scheduled(fixedRateString = RUN_INTERVAL, initialDelayString = RUN_INTERVAL)
    public void run() {
        resubmitFailedPublications();
        completedEventPublications.deletePublicationsOlderThan(properties.completedRetention());
    }

    private void resubmitFailedPublications() {
        failedEventPublications.resubmit(ResubmissionOptions.defaults()
                .withMinAge(properties.resubmitMinAge())
                .withFilter(this::hasAttemptsLeft));
    }

    // completion_attempts starts at 1 for the initial delivery and grows by one per resubmission,
    // so the limit caps the total number of delivery attempts. Without it, a publication that can
    // never succeed (e.g. for a member deleted in the meantime) would be retried forever.
    private boolean hasAttemptsLeft(EventPublication publication) {
        return publication.getCompletionAttempts() < properties.maxAttempts();
    }
}
