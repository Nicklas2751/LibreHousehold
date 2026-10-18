package eu.wiegandt.librehousehold.config;

import ch.martinelli.oss.testcontainers.mailpit.MailpitClient;
import ch.martinelli.oss.testcontainers.mailpit.assertions.MailpitAssertions;
import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.VerificationDeletionWarningRequested;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Each publication is created through the real publish path (so listener ID and serialized event
 * come from Spring Modulith itself), delivered once, and then moved into the state under test via
 * SQL: a real listener failure would need an unreachable mail server, which the shared Mailpit
 * container cannot simulate.
 *
 * <p>The run-interval property is set only to give this class its own application context and
 * therefore its own database: {@code job.run()} resubmits every failed publication in the
 * database, and publications left behind by other IT classes must not be retried here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "librehousehold.security.oauth2-client.client-secret=test-client-secret",
        "librehousehold.events.run-interval=1d"})
@Import(TestcontainersConfiguration.class)
class EventPublicationMaintenanceJobIT {

    private static final Duration MAIL_DELIVERY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LISTENER_COMPLETION_TIMEOUT = Duration.ofSeconds(10);
    // Must match librehousehold.events.max-attempts / completed-retention in application.yaml.
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration BEYOND_COMPLETED_RETENTION = Duration.ofDays(31);
    private static final Duration BEYOND_RESUBMIT_MIN_AGE = Duration.ofHours(1);
    private static final int ATTEMPTS_AFTER_FIRST_DELIVERY = 1;

    private static final String FIND_PUBLICATION_ID_SQL = """
            SELECT id FROM event_publication
            WHERE status = 'COMPLETED' AND serialized_event LIKE '%' || ? || '%'
            """;
    private static final String MARK_FAILED_SQL = """
            UPDATE event_publication
            SET status = 'FAILED', completion_date = NULL, publication_date = ?, completion_attempts = ?
            WHERE id = ?
            """;
    private static final String MARK_COMPLETED_AT_SQL = "UPDATE event_publication SET completion_date = ? WHERE id = ?";
    private static final String FIND_STATUS_SQL = "SELECT status FROM event_publication WHERE id = ?";
    private static final String COUNT_BY_ID_SQL = "SELECT COUNT(*) FROM event_publication WHERE id = ?";

    @Autowired
    private EventPublicationMaintenanceJob job;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionOperations transactionOperations;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MailpitClient mailpitClient;

    @Nested
    class run {

        @Test
        void failedPublicationBelowMaxAttempts_deliversEventAgain() {
            // given — two messages: the original delivery plus the resubmitted one
            var memberId = UUID.randomUUID();
            var email = memberId + "@example.com";
            var publicationId = publishAndAwaitCompletion(new VerificationDeletionWarningRequested(memberId, email));
            jdbcTemplate.update(MARK_FAILED_SQL, Timestamp.from(Instant.now().minus(BEYOND_RESUBMIT_MIN_AGE)), ATTEMPTS_AFTER_FIRST_DELIVERY,
                    publicationId);

            // when
            job.run();

            // then
            await().atMost(MAIL_DELIVERY_TIMEOUT)
                    .untilAsserted(() -> MailpitAssertions.assertThat(mailpitClient.getAllMessages())
                            .filteredOnRecipient(email)
                            .hasSize(2));
        }

        @Test
        void failedPublicationAtMaxAttempts_staysFailed() {
            // given
            var memberId = UUID.randomUUID();
            var publicationId = publishAndAwaitCompletion(
                    new VerificationDeletionWarningRequested(memberId, memberId + "@example.com"));
            jdbcTemplate.update(MARK_FAILED_SQL, Timestamp.from(Instant.now().minus(BEYOND_RESUBMIT_MIN_AGE)), MAX_ATTEMPTS,
                    publicationId);

            // when
            job.run();

            // then
            assertThat(jdbcTemplate.queryForObject(FIND_STATUS_SQL, String.class, publicationId)).isEqualTo("FAILED");
        }

        @Test
        void completedPublicationOlderThanRetention_deletesPublication() {
            // given
            var memberId = UUID.randomUUID();
            var publicationId = publishAndAwaitCompletion(
                    new VerificationDeletionWarningRequested(memberId, memberId + "@example.com"));
            jdbcTemplate.update(MARK_COMPLETED_AT_SQL, Timestamp.from(Instant.now().minus(BEYOND_COMPLETED_RETENTION)),
                    publicationId);

            // when
            job.run();

            // then
            assertThat(jdbcTemplate.queryForObject(COUNT_BY_ID_SQL, Long.class, publicationId)).isZero();
        }
    }

    private UUID publishAndAwaitCompletion(VerificationDeletionWarningRequested event) {
        // @ApplicationModuleListener only runs after commit, so the event needs a transaction.
        transactionOperations.executeWithoutResult(_ -> eventPublisher.publishEvent(event));
        return await().atMost(LISTENER_COMPLETION_TIMEOUT)
                .until(() -> findCompletedPublicationId(event.memberId()), id -> id != null);
    }

    private UUID findCompletedPublicationId(UUID memberId) {
        return jdbcTemplate.query(FIND_PUBLICATION_ID_SQL,
                resultSet -> resultSet.next() ? resultSet.getObject(1, UUID.class) : null, memberId.toString());
    }
}
