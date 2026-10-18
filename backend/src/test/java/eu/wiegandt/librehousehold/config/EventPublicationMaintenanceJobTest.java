package eu.wiegandt.librehousehold.config;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventPublicationMaintenanceJobTest {

    private static final Duration RESUBMIT_MIN_AGE = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration COMPLETED_RETENTION = Duration.ofDays(30);

    @Mock
    private FailedEventPublications failedEventPublications;

    @Mock
    private CompletedEventPublications completedEventPublications;

    @Nested
    class run {

        @Test
        void anyState_resubmitsOnlyFailedPublicationsOlderThanMinAge() {
            // given
            var job = buildJob();
            var options = ArgumentCaptor.forClass(ResubmissionOptions.class);

            // when
            job.run();

            // then
            verify(failedEventPublications).resubmit(options.capture());
            assertThat(options.getValue().getMinAge()).isEqualTo(RESUBMIT_MIN_AGE);
        }

        @ParameterizedTest
        @CsvSource({"1, true", "4, true", "5, false", "6, false"})
        void publicationWithCompletionAttempts_isResubmittedOnlyBelowMaxAttempts(int completionAttempts,
                                                                                 boolean expectedResubmitted) {
            // given
            var job = buildJob();
            var options = ArgumentCaptor.forClass(ResubmissionOptions.class);

            // when
            job.run();

            // then
            verify(failedEventPublications).resubmit(options.capture());
            assertThat(options.getValue().getFilter().test(new FailedPublication(completionAttempts)))
                    .isEqualTo(expectedResubmitted);
        }

        @Test
        void anyState_deletesCompletedPublicationsOlderThanRetention() {
            // given
            var job = buildJob();

            // when
            job.run();

            // then
            verify(completedEventPublications).deletePublicationsOlderThan(COMPLETED_RETENTION);
        }
    }

    private EventPublicationMaintenanceJob buildJob() {
        return new EventPublicationMaintenanceJob(failedEventPublications, completedEventPublications,
                new EventPublicationMaintenanceProperties(RESUBMIT_MIN_AGE, MAX_ATTEMPTS, COMPLETED_RETENTION));
    }

    // EventPublication is a framework value type with no public factory for arbitrary attempt
    // counts, so the test provides its own minimal implementation instead of mocking it.
    private record FailedPublication(int completionAttempts) implements EventPublication {

        @Override
        public UUID getIdentifier() {
            return UUID.randomUUID();
        }

        @Override
        public Object getEvent() {
            return new Object();
        }

        @Override
        public Instant getPublicationDate() {
            return Instant.now();
        }

        @Override
        public Optional<Instant> getCompletionDate() {
            return Optional.empty();
        }

        @Override
        public Status getStatus() {
            return Status.FAILED;
        }

        @Override
        public @Nullable Instant getLastResubmissionDate() {
            return null;
        }

        @Override
        public int getCompletionAttempts() {
            return completionAttempts;
        }
    }
}
