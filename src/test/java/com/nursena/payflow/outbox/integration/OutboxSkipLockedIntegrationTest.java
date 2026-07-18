package com.nursena.payflow.outbox.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.nursena.payflow.outbox.application.port.out.OutboxEventClaimPort;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class OutboxSkipLockedIntegrationTest {

    private static final UUID FIRST_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000011"
        );

    private static final UUID SECOND_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000012"
        );

    private static final UUID AGGREGATE_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000000011"
        );

    private static final String EVENT_TYPE =
        "wallet.transfer.completed";

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-18T11:00:00Z"
        );

    private static final Instant CLAIMED_AT =
        CREATED_AT.plusSeconds(60);

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private OutboxEventClaimPort claimPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager
        transactionManager;

    @BeforeEach
    void setUpDatabase() {
        jdbcTemplate.update(
            "DELETE FROM outbox_events"
        );

        insertPendingEvent(FIRST_EVENT_ID);
        insertPendingEvent(SECOND_EVENT_ID);
    }

    @Test
    void shouldSkipLockedRowAndClaimNextAvailableEvent()
        throws Exception {

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        CountDownLatch lockAcquired =
            new CountDownLatch(1);

        CountDownLatch releaseLock =
            new CountDownLatch(1);

        TransactionTemplate transactionTemplate =
            new TransactionTemplate(
                transactionManager
            );

        Future<?> lockingTransaction =
            executor.submit(() ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                        jdbcTemplate.queryForObject(
                            """
                            SELECT id::text
                            FROM outbox_events
                            WHERE id = ?
                            FOR UPDATE
                            """,
                            String.class,
                            FIRST_EVENT_ID
                        );

                        lockAcquired.countDown();

                        await(releaseLock);
                    }
                )
            );

        try {
            assertThat(
                lockAcquired.await(5, SECONDS)
            ).isTrue();

            Future<List<OutboxEvent>>
                claimOperation =
                executor.submit(() ->
                    claimPort.claimAvailable(
                        "publisher-2",
                        CLAIMED_AT,
                        Duration.ofSeconds(30),
                        1
                    )
                );

            List<OutboxEvent> claimed =
                claimOperation.get(
                    5,
                    SECONDS
                );

            assertThat(claimed)
                .extracting(OutboxEvent::id)
                .containsExactly(
                    SECOND_EVENT_ID
                );

            assertThat(
                statusOf(FIRST_EVENT_ID)
            ).isEqualTo("PENDING");

            assertThat(
                statusOf(SECOND_EVENT_ID)
            ).isEqualTo("PROCESSING");
        } finally {
            releaseLock.countDown();

            lockingTransaction.get(
                5,
                SECONDS
            );

            executor.shutdownNow();
        }

        List<OutboxEvent> afterRelease =
            claimPort.claimAvailable(
                "publisher-3",
                CLAIMED_AT,
                Duration.ofSeconds(30),
                1
            );

        assertThat(afterRelease)
            .extracting(OutboxEvent::id)
            .containsExactly(
                FIRST_EVENT_ID
            );
    }

    private void insertPendingEvent(
        UUID eventId
    ) {
        String payload = """
            {
              "eventId": "%s",
              "eventType": "wallet.transfer.completed",
              "eventVersion": 1
            }
            """.formatted(eventId);

        jdbcTemplate.update(
            """
            INSERT INTO outbox_events (
                id,
                aggregate_type,
                aggregate_id,
                event_type,
                event_version,
                topic,
                partition_key,
                deduplication_key,
                payload,
                status,
                attempt_count,
                available_at,
                created_at
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?,
                CAST(? AS jsonb),
                'PENDING',
                0,
                ?,
                ?
            )
            """,
            eventId,
            "PAYMENT_TRANSACTION",
            AGGREGATE_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            AGGREGATE_ID.toString(),
            EVENT_TYPE + ":1:" + eventId,
            payload,
            Timestamp.from(CREATED_AT),
            Timestamp.from(CREATED_AT)
        );
    }

    private String statusOf(
        UUID eventId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM outbox_events
            WHERE id = ?
            """,
            String.class,
            eventId
        );
    }

    private static void await(
        CountDownLatch latch
    ) {
        try {
            boolean released =
                latch.await(10, SECONDS);

            if (!released) {
                throw new IllegalStateException(
                    "Timed out while waiting "
                        + "to release row lock."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                "Interrupted while waiting "
                    + "to release row lock.",
                exception
            );
        }
    }
}
