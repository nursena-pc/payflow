package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ProcessedKafkaEventSchemaIntegrationTest {

    private static final String NOTIFICATION_CONSUMER =
        "transfer-completed-notification";

    private static final String AUDIT_CONSUMER =
        "transfer-completed-audit";

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000201"
        );

    private static final String EVENT_TYPE =
        "wallet.transfer.completed";

    private static final Instant PROCESSED_AT =
        Instant.parse(
            "2026-07-20T17:00:00Z"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM processed_kafka_events"
        );
    }

    @Test
    void shouldPersistValidProcessedEvent() {
        insertProcessedEvent(
            NOTIFICATION_CONSUMER,
            EVENT_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            0,
            15L
        );

        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM processed_kafka_events
            WHERE consumer_name = ?
              AND event_id = ?
            """,
            Integer.class,
            NOTIFICATION_CONSUMER,
            EVENT_ID
        );

        Long recordOffset = jdbcTemplate.queryForObject(
            """
            SELECT record_offset
            FROM processed_kafka_events
            WHERE consumer_name = ?
              AND event_id = ?
            """,
            Long.class,
            NOTIFICATION_CONSUMER,
            EVENT_ID
        );

        assertThat(count)
            .isEqualTo(1);

        assertThat(recordOffset)
            .isEqualTo(15L);
    }

    @Test
    void shouldRejectDuplicateEventForSameConsumer() {
        insertProcessedEvent(
            NOTIFICATION_CONSUMER,
            EVENT_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            0,
            15L
        );

        assertConstraintViolation(
            () -> insertProcessedEvent(
                NOTIFICATION_CONSUMER,
                EVENT_ID,
                EVENT_TYPE,
                1,
                EVENT_TYPE,
                0,
                16L
            ),
            "pk_processed_kafka_events"
        );
    }

    @Test
    void shouldAllowSameEventForDifferentConsumers() {
        insertProcessedEvent(
            NOTIFICATION_CONSUMER,
            EVENT_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            0,
            15L
        );

        insertProcessedEvent(
            AUDIT_CONSUMER,
            EVENT_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            0,
            15L
        );

        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM processed_kafka_events
            WHERE event_id = ?
            """,
            Integer.class,
            EVENT_ID
        );

        assertThat(count)
            .isEqualTo(2);
    }

    @Test
    void shouldRejectBlankConsumerName() {
        assertConstraintViolation(
            () -> insertProcessedEvent(
                " ",
                EVENT_ID,
                EVENT_TYPE,
                1,
                EVENT_TYPE,
                0,
                15L
            ),
            "chk_processed_kafka_events_consumer_name"
        );
    }

    @Test
    void shouldRejectBlankEventType() {
        assertConstraintViolation(
            () -> insertProcessedEvent(
                NOTIFICATION_CONSUMER,
                EVENT_ID,
                " ",
                1,
                EVENT_TYPE,
                0,
                15L
            ),
            "chk_processed_kafka_events_event_type"
        );
    }

    @Test
    void shouldRejectNonPositiveEventVersion() {
        assertConstraintViolation(
            () -> insertProcessedEvent(
                NOTIFICATION_CONSUMER,
                EVENT_ID,
                EVENT_TYPE,
                0,
                EVENT_TYPE,
                0,
                15L
            ),
            "chk_processed_kafka_events_event_version"
        );
    }

    @Test
    void shouldRejectBlankTopic() {
        assertConstraintViolation(
            () -> insertProcessedEvent(
                NOTIFICATION_CONSUMER,
                EVENT_ID,
                EVENT_TYPE,
                1,
                " ",
                0,
                15L
            ),
            "chk_processed_kafka_events_topic"
        );
    }

    @Test
    void shouldRejectNegativePartition() {
        assertConstraintViolation(
            () -> insertProcessedEvent(
                NOTIFICATION_CONSUMER,
                EVENT_ID,
                EVENT_TYPE,
                1,
                EVENT_TYPE,
                -1,
                15L
            ),
            "chk_processed_kafka_events_partition"
        );
    }

    @Test
    void shouldRejectNegativeOffset() {
        assertConstraintViolation(
            () -> insertProcessedEvent(
                NOTIFICATION_CONSUMER,
                EVENT_ID,
                EVENT_TYPE,
                1,
                EVENT_TYPE,
                0,
                -1L
            ),
            "chk_processed_kafka_events_offset"
        );
    }

    private void insertProcessedEvent(
        String consumerName,
        UUID eventId,
        String eventType,
        int eventVersion,
        String topic,
        int partitionNumber,
        long recordOffset
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO processed_kafka_events (
                consumer_name,
                event_id,
                event_type,
                event_version,
                topic,
                partition_number,
                record_offset,
                processed_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            consumerName,
            eventId,
            eventType,
            eventVersion,
            topic,
            partitionNumber,
            recordOffset,
            Timestamp.from(PROCESSED_AT)
        );
    }

    private static void assertConstraintViolation(
        ThrowingCallable operation,
        String constraintName
    ) {
        Throwable thrown = catchThrowable(
            operation
        );

        assertThat(thrown)
            .isInstanceOf(
                DataIntegrityViolationException.class
            );

        assertThat(rootCauseOf(thrown).getMessage())
            .contains(constraintName);
    }

    private static Throwable rootCauseOf(
        Throwable throwable
    ) {
        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }
}
