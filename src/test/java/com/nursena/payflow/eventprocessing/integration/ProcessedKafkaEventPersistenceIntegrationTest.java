package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.nursena.payflow.eventprocessing.application.port.out.ProcessedKafkaEventRepositoryPort;
import com.nursena.payflow.eventprocessing.domain.model.ProcessedKafkaEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ProcessedKafkaEventPersistenceIntegrationTest {

    private static final String NOTIFICATION_CONSUMER =
        "transfer-completed-notification";

    private static final String AUDIT_CONSUMER =
        "transfer-completed-audit";

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000401"
        );

    private static final Instant PROCESSED_AT =
        Instant.parse(
            "2026-07-20T19:00:00Z"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private ProcessedKafkaEventRepositoryPort
        repositoryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM processed_kafka_events"
        );
    }

    @Test
    void shouldRecordEventForFirstDelivery() {
        boolean recorded =
            repositoryPort.tryRecord(
                processedEvent(
                    NOTIFICATION_CONSUMER
                )
            );

        assertThat(recorded)
            .isTrue();

        Integer count = countByEventId(
            EVENT_ID
        );

        assertThat(count)
            .isEqualTo(1);
    }

    @Test
    void shouldReturnFalseForDuplicateDelivery() {
        boolean firstResult =
            repositoryPort.tryRecord(
                processedEvent(
                    NOTIFICATION_CONSUMER
                )
            );

        boolean duplicateResult =
            repositoryPort.tryRecord(
                processedEvent(
                    NOTIFICATION_CONSUMER
                )
            );

        assertThat(firstResult)
            .isTrue();

        assertThat(duplicateResult)
            .isFalse();

        assertThat(
            countByEventId(EVENT_ID)
        )
            .isEqualTo(1);
    }

    @Test
    void shouldAllowSameEventForDifferentConsumers() {
        boolean notificationResult =
            repositoryPort.tryRecord(
                processedEvent(
                    NOTIFICATION_CONSUMER
                )
            );

        boolean auditResult =
            repositoryPort.tryRecord(
                processedEvent(
                    AUDIT_CONSUMER
                )
            );

        assertThat(notificationResult)
            .isTrue();

        assertThat(auditResult)
            .isTrue();

        assertThat(
            countByEventId(EVENT_ID)
        )
            .isEqualTo(2);
    }

    @Test
    void shouldRecordOnlyOnceUnderConcurrentDelivery()
        throws Exception {

        int workerCount = 8;

        ExecutorService executor =
            Executors.newFixedThreadPool(
                workerCount
            );

        CountDownLatch ready =
            new CountDownLatch(
                workerCount
            );

        CountDownLatch start =
            new CountDownLatch(1);

        List<Future<Boolean>> results =
            new ArrayList<>();

        try {
            for (int index = 0;
                 index < workerCount;
                 index++) {

                results.add(
                    executor.submit(
                        () -> {
                            ready.countDown();

                            boolean started =
                                start.await(
                                    10,
                                    TimeUnit.SECONDS
                                );

                            if (!started) {
                                throw new IllegalStateException(
                                    "Concurrent test start "
                                        + "timed out."
                                );
                            }

                            return repositoryPort.tryRecord(
                                processedEvent(
                                    NOTIFICATION_CONSUMER
                                )
                            );
                        }
                    )
                );
            }

            assertThat(
                ready.await(
                    10,
                    TimeUnit.SECONDS
                )
            )
                .isTrue();

            start.countDown();

            long successfulInsertCount = 0;

            for (Future<Boolean> result : results) {
                if (result.get(
                    10,
                    TimeUnit.SECONDS
                )) {
                    successfulInsertCount++;
                }
            }

            assertThat(successfulInsertCount)
                .isEqualTo(1);

            assertThat(
                countByEventId(EVENT_ID)
            )
                .isEqualTo(1);
        } finally {
            executor.shutdownNow();

            assertThat(
                executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
                )
            )
                .isTrue();
        }
    }

    private Integer countByEventId(
        UUID eventId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM processed_kafka_events
            WHERE event_id = ?
            """,
            Integer.class,
            eventId
        );
    }

    private static ProcessedKafkaEvent
    processedEvent(
        String consumerName
    ) {
        return new ProcessedKafkaEvent(
            consumerName,
            EVENT_ID,
            "wallet.transfer.completed",
            1,
            "wallet.transfer.completed",
            0,
            15L,
            PROCESSED_AT
        );
    }
}
