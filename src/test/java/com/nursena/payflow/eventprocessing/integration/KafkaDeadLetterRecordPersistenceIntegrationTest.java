package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterRecordRepositoryPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
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
class KafkaDeadLetterRecordPersistenceIntegrationTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001101"
        );

    private static final Instant RECEIVED_AT =
        Instant.parse(
            "2026-07-21T17:00:00.123456Z"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private KafkaDeadLetterRecordRepositoryPort
        repositoryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM kafka_dead_letter_records"
        );
    }

    @Test
    void shouldRecordFirstDeadLetterDelivery() {
        boolean recorded =
            repositoryPort.tryRecord(
                deadLetterRecord(RECORD_ID)
            );

        assertThat(recorded)
            .isTrue();

        Map<String, Object> stored =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    dlt_topic,
                    dlt_partition,
                    dlt_offset,
                    original_topic,
                    status,
                    replay_count,
                    record_key,
                    payload,
                    replay_origin_id,
                    replay_attempt_base
                FROM kafka_dead_letter_records
                WHERE id = ?
                """,
                RECORD_ID
            );

        assertThat(
            stored.get("dlt_topic")
        )
            .isEqualTo(
                "wallet.transfer.completed.dlt"
            );

        assertThat(
            stored.get("dlt_partition")
        )
            .isEqualTo(0);

        assertThat(
            ((Number) stored.get(
                "dlt_offset"
            )).longValue()
        )
            .isEqualTo(25L);

        assertThat(
            stored.get("original_topic")
        )
            .isEqualTo(
                "wallet.transfer.completed"
            );

        assertThat(
            stored.get("status")
        )
            .isEqualTo("RECEIVED");

        assertThat(
            stored.get("replay_count")
        )
            .isEqualTo(0);

        assertThat(
            stored.get("record_key")
        )
            .isNull();

        assertThat(
            stored.get("payload")
        )
            .isNull();

        assertThat(
            stored.get("replay_origin_id")
        )
            .isEqualTo(RECORD_ID);

        assertThat(
            stored.get("replay_attempt_base")
        )
            .isEqualTo(0);
    }

    @Test
    void shouldReturnFalseForDuplicateDeadLetterLocation() {
        boolean firstResult =
            repositoryPort.tryRecord(
                deadLetterRecord(RECORD_ID)
            );

        boolean duplicateResult =
            repositoryPort.tryRecord(
                deadLetterRecord(
                    UUID.fromString(
                        "80000000-0000-0000-0000-000000001102"
                    )
                )
            );

        assertThat(firstResult)
            .isTrue();

        assertThat(duplicateResult)
            .isFalse();

        assertThat(countRecords())
            .isEqualTo(1);
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
            for (
                int index = 0;
                index < workerCount;
                index++
            ) {
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
                                throw new
                                    IllegalStateException(
                                    "Concurrent test "
                                        + "start timed out."
                                );
                            }

                            return repositoryPort
                                .tryRecord(
                                    deadLetterRecord(
                                        UUID.randomUUID()
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

            for (
                Future<Boolean> result
                : results
            ) {
                if (
                    result.get(
                        10,
                        TimeUnit.SECONDS
                    )
                ) {
                    successfulInsertCount++;
                }
            }

            assertThat(successfulInsertCount)
                .isEqualTo(1);

            assertThat(countRecords())
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

    @Test
    void shouldPreserveReplayLineage() {
        UUID derivedRecordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000001103"
            );

        KafkaDeadLetterRecord record =
            new KafkaDeadLetterRecord(
                derivedRecordId,
                "wallet.transfer.completed.dlt",
                0,
                30L,
                "wallet.transfer.completed",
                0,
                10L,
                "payflow-transfer-completed-audit-v1",
                "transaction-id",
                "{}",
                "IllegalStateException",
                "Temporary failure.",
                KafkaDeadLetterRecordStatus.RECEIVED,
                0,
                RECEIVED_AT,
                null,
                null,
                null,
                null,
                RECORD_ID,
                2
            );

        assertThat(
            repositoryPort.tryRecord(record)
        )
            .isTrue();

        Map<String, Object> stored =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    replay_origin_id,
                    replay_attempt_base
                FROM kafka_dead_letter_records
                WHERE id = ?
                """,
                derivedRecordId
            );

        assertThat(
            stored.get("replay_origin_id")
        )
            .isEqualTo(RECORD_ID);

        assertThat(
            stored.get("replay_attempt_base")
        )
            .isEqualTo(2);
    }

    private Integer countRecords() {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM kafka_dead_letter_records
            """,
            Integer.class
        );
    }

    private static KafkaDeadLetterRecord
    deadLetterRecord(
        UUID id
    ) {
        return new KafkaDeadLetterRecord(
            id,
            "wallet.transfer.completed.dlt",
            0,
            25L,
            "wallet.transfer.completed",
            0,
            10L,
            "payflow-transfer-completed-audit-v1",
            null,
            null,
            "IllegalStateException",
            "Temporary failure.",
            KafkaDeadLetterRecordStatus.RECEIVED,
            0,
            RECEIVED_AT,
            null,
            null,
            null,
            null
        );
    }
}
