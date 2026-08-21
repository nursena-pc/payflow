package com.nursena.payflow.configuration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.github.dockerjava.api.DockerClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.eventprocessing.adapter.kafka
    .KafkaDeadLetterReplayHeaders;
import com.nursena.payflow.eventprocessing.application.model
    .ReplayKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model
    .ReplayKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.in
    .ReplayKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.outbox.application.port.in
    .PublishOutboxEventsCommand;
import com.nursena.payflow.outbox.application.port.in
    .PublishOutboxEventsResult;
import com.nursena.payflow.outbox.application.port.in
    .PublishOutboxEventsUseCase;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(properties = {
    "payflow.security.login-rate-limit.enabled=false",
    "payflow.outbox.polling.enabled=false",
    "payflow.mail.outbox.polling.enabled=false",
    "payflow.outbox.kafka.send-timeout=10s",
    "payflow.outbox.retry.max-attempts=5",
    "payflow.outbox.retry.initial-delay=10s",
    "payflow.outbox.retry.maximum-delay=1m",
    "payflow.event-processing.transfer-completed.enabled=true",
    "payflow.event-processing.transfer-completed.topic=wallet.transfer.completed.v016-outage",
    "payflow.event-processing.transfer-completed.group-id=v016-outage-consumer",
    "payflow.event-processing.transfer-completed.consumer-name=v016-outage-consumer",
    "payflow.event-processing.transfer-completed.failure.dead-letter-topic=wallet.transfer.completed.v016-outage.dlt",
    "payflow.event-processing.transfer-completed.failure.max-retries=3",
    "payflow.event-processing.transfer-completed.failure.initial-delay=500ms",
    "payflow.event-processing.transfer-completed.failure.multiplier=2.0",
    "payflow.event-processing.transfer-completed.failure.maximum-delay=5s",
    "payflow.event-processing.transfer-completed.failure.send-timeout=10s",
    "payflow.event-processing.transfer-completed.dead-letter-intake.enabled=true",
    "payflow.event-processing.transfer-completed.dead-letter-intake.group-id=v016-outage-dlt-intake",
    "payflow.event-processing.transfer-completed.dead-letter-replay.worker-id=v016-outage-replay",
    "payflow.event-processing.transfer-completed.dead-letter-replay.lease-duration=30s",
    "payflow.event-processing.transfer-completed.dead-letter-replay.max-attempts=3",
    "payflow.event-processing.transfer-completed.dead-letter-replay.send-timeout=10s",
    "spring.kafka.consumer.enable-auto-commit=false",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.listener.ack-mode=record"
})
@Testcontainers
@Import(
    V016KafkaOutageRecoveryRehearsalTest
        .KafkaTopicConfiguration.class
)
class V016KafkaOutageRecoveryRehearsalTest {

    private static final String SOURCE_TOPIC =
        "wallet.transfer.completed.v016-outage";

    private static final String DLT_TOPIC =
        SOURCE_TOPIC + ".dlt";

    private static final String CONSUMER_NAME =
        "v016-outage-consumer";

    private static final UUID HEALTHY_EVENT_ID =
        UUID.fromString(
            "92000000-0000-0000-0000-000000000101"
        );

    private static final UUID HEALTHY_TX_ID =
        UUID.fromString(
            "93000000-0000-0000-0000-000000000101"
        );

    private static final UUID RECOVERY_EVENT_ID =
        UUID.fromString(
            "92000000-0000-0000-0000-000000000102"
        );

    private static final UUID RECOVERY_TX_ID =
        UUID.fromString(
            "93000000-0000-0000-0000-000000000102"
        );

    private static final UUID REPLAY_RECORD_ID =
        UUID.fromString(
            "94000000-0000-0000-0000-000000000101"
        );

    private static final UUID REPLAY_EVENT_ID =
        UUID.fromString(
            "92000000-0000-0000-0000-000000000103"
        );

    private static final UUID REPLAY_TX_ID =
        UUID.fromString(
            "93000000-0000-0000-0000-000000000103"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Container
    @ServiceConnection
    private static final KafkaContainer KAFKA =
        new KafkaContainer(
            "apache/kafka:4.1.2"
        );

    @Autowired
    private PublishOutboxEventsUseCase
        publishOutboxEvents;

    @Autowired
    private ReplayKafkaDeadLetterRecordUseCase
        replayDeadLetterRecord;

    @Autowired
    private KafkaTemplate<String, String>
        kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldKeepDurableOutboxAndReplayStateAcrossBrokerOutage()
        throws Exception {

        cleanDatabase();

        int paymentTransactionsBefore =
            count("payment_transactions");
        int ledgerEntriesBefore =
            count("ledger_entries");

        try (
            KafkaConsumer<String, String> observer =
                observerConsumer()
        ) {
            observer.subscribe(
                List.of(SOURCE_TOPIC)
            );

            String healthyPayload =
                payload(
                    HEALTHY_EVENT_ID,
                    HEALTHY_TX_ID,
                    "2026-08-21T12:00:00Z"
                );

            insertOutboxEvent(
                HEALTHY_EVENT_ID,
                HEALTHY_TX_ID,
                healthyPayload
            );

            PublishOutboxEventsResult healthy =
                publishOne();

            assertThat(healthy.claimedCount())
                .isEqualTo(1);
            assertThat(healthy.publishedCount())
                .isEqualTo(1);
            assertThat(healthy.retriedCount())
                .isZero();

            assertOutboxState(
                HEALTHY_EVENT_ID,
                "PUBLISHED",
                1,
                true,
                false
            );

            ConsumerRecord<String, String>
                healthyRecord =
                awaitRecord(
                    observer,
                    HEALTHY_EVENT_ID
                );

            assertThat(healthyRecord.key())
                .isEqualTo(
                    HEALTHY_TX_ID.toString()
                );
            assertJsonEquivalent(
                healthyRecord.value(),
                healthyPayload
            );

            await(
                () ->
                    processedCount(
                        HEALTHY_EVENT_ID
                    ) == 1
                        && auditCount(
                            HEALTHY_EVENT_ID
                        ) == 1,
                "healthy transfer-completed persistence"
            );

            String recoveryPayload =
                payload(
                    RECOVERY_EVENT_ID,
                    RECOVERY_TX_ID,
                    "2026-08-21T12:01:00Z"
                );

            insertOutboxEvent(
                RECOVERY_EVENT_ID,
                RECOVERY_TX_ID,
                recoveryPayload
            );

            pauseKafka();

            try {
                PublishOutboxEventsResult outage =
                    publishOne();

                assertThat(outage.claimedCount())
                    .isEqualTo(1);
                assertThat(outage.publishedCount())
                    .isZero();
                assertThat(outage.retriedCount())
                    .isEqualTo(1);

                assertOutboxState(
                    RECOVERY_EVENT_ID,
                    "PENDING",
                    1,
                    false,
                    true
                );

                assertThat(
                    count("payment_transactions")
                )
                    .isEqualTo(
                        paymentTransactionsBefore
                    );

                assertThat(
                    count("ledger_entries")
                )
                    .isEqualTo(
                        ledgerEntriesBefore
                    );
            } finally {
                unpauseKafka();
                awaitKafka();
            }

            waitUntilOutboxAvailable(
                RECOVERY_EVENT_ID
            );

            PublishOutboxEventsResult recovered =
                publishOne();

            assertThat(recovered.claimedCount())
                .isEqualTo(1);
            assertThat(recovered.publishedCount())
                .isEqualTo(1);
            assertThat(recovered.retriedCount())
                .isZero();

            assertOutboxState(
                RECOVERY_EVENT_ID,
                "PUBLISHED",
                2,
                true,
                false
            );

            ConsumerRecord<String, String>
                recoveryRecord =
                awaitRecord(
                    observer,
                    RECOVERY_EVENT_ID
                );

            assertThat(recoveryRecord.key())
                .isEqualTo(
                    RECOVERY_TX_ID.toString()
                );
            assertJsonEquivalent(
                recoveryRecord.value(),
                recoveryPayload
            );

            await(
                () ->
                    processedCount(
                        RECOVERY_EVENT_ID
                    ) == 1
                        && auditCount(
                            RECOVERY_EVENT_ID
                        ) == 1,
                "recovered outbox event persistence"
            );

            kafkaTemplate
                .send(
                    SOURCE_TOPIC,
                    "invalid-dlt-key",
                    "{invalid-json"
                )
                .get(
                    20,
                    TimeUnit.SECONDS
                );

            await(
                () ->
                    dltRecordCountByKey(
                        "invalid-dlt-key"
                    ) == 1,
                "durable DLT intake"
            );

            Map<String, Object> receivedDlt =
                jdbcTemplate.queryForMap(
                    """
                    SELECT
                        id,
                        status,
                        replay_count,
                        replay_origin_id,
                        replay_attempt_base
                    FROM kafka_dead_letter_records
                    WHERE record_key = ?
                    """,
                    "invalid-dlt-key"
                );

            assertThat(receivedDlt.get("status"))
                .isEqualTo("RECEIVED");
            assertThat(
                ((Number) receivedDlt.get(
                    "replay_count"
                )).intValue()
            )
                .isZero();

            insertReplayableDeadLetterRecord();

            pauseKafka();

            try {
                ReplayKafkaDeadLetterRecordResult
                    failedReplay =
                    replayDeadLetterRecord.replay(
                        new ReplayKafkaDeadLetterRecordCommand(
                            REPLAY_RECORD_ID
                        )
                    );

                assertThat(
                    failedReplay.isReplayFailed()
                )
                    .isTrue();

                ReplayState failedState =
                    replayState();

                assertThat(failedState.status())
                    .isEqualTo("REPLAY_FAILED");
                assertThat(failedState.replayCount())
                    .isEqualTo(1);
                assertThat(failedState.leaseOwner())
                    .isNull();
                assertThat(failedState.leaseUntil())
                    .isNull();
                assertThat(failedState.lastError())
                    .isNotBlank();
                assertThat(failedState.originId())
                    .isEqualTo(REPLAY_RECORD_ID);
                assertThat(failedState.attemptBase())
                    .isZero();
            } finally {
                unpauseKafka();
                awaitKafka();
            }

            ReplayKafkaDeadLetterRecordResult
                successfulReplay =
                replayDeadLetterRecord.replay(
                    new ReplayKafkaDeadLetterRecordCommand(
                        REPLAY_RECORD_ID
                    )
                );

            assertThat(
                successfulReplay.isReplayed()
            )
                .isTrue();

            ReplayState replayedState =
                replayState();

            assertThat(replayedState.status())
                .isEqualTo("REPLAYED");
            assertThat(replayedState.replayCount())
                .isEqualTo(2);
            assertThat(replayedState.leaseOwner())
                .isNull();
            assertThat(replayedState.leaseUntil())
                .isNull();
            assertThat(replayedState.lastError())
                .isNull();
            assertThat(replayedState.originId())
                .isEqualTo(REPLAY_RECORD_ID);
            assertThat(replayedState.attemptBase())
                .isZero();

            ConsumerRecord<String, String>
                replayedRecord =
                awaitReplayAttempt(
                    observer,
                    REPLAY_EVENT_ID,
                    REPLAY_RECORD_ID,
                    2
                );

            assertThat(
                headerValue(
                    replayedRecord,
                    KafkaDeadLetterReplayHeaders
                        .REPLAY_ORIGIN_ID
                )
            )
                .isEqualTo(
                    REPLAY_RECORD_ID.toString()
                );

            assertThat(
                headerValue(
                    replayedRecord,
                    KafkaDeadLetterReplayHeaders
                        .REPLAY_ATTEMPT
                )
            )
                .isEqualTo("2");

            await(
                () ->
                    processedCount(
                        REPLAY_EVENT_ID
                    ) == 1
                        && auditCount(
                            REPLAY_EVENT_ID
                        ) == 1,
                "replayed transfer-completed persistence"
            );

            /*
             * The first replay send may time out at the
             * application acknowledgement boundary and
             * still complete later after broker recovery.
             * Once attempt 2 is observed, give the
             * consumer a short grace interval and prove
             * that the PostgreSQL idempotency/audit
             * boundary remains single-record.
             */
            TimeUnit.SECONDS.sleep(2);

            assertThat(
                processedCount(
                    REPLAY_EVENT_ID
                )
            )
                .isEqualTo(1);

            assertThat(
                auditCount(
                    REPLAY_EVENT_ID
                )
            )
                .isEqualTo(1);

            assertThat(
                count("payment_transactions")
            )
                .isEqualTo(
                    paymentTransactionsBefore
                );

            assertThat(
                count("ledger_entries")
            )
                .isEqualTo(
                    ledgerEntriesBefore
                );

            assertThat(
                dltRecordCountByKey(
                    "invalid-dlt-key"
                )
            )
                .isEqualTo(1);
        }
    }

    private PublishOutboxEventsResult
    publishOne() {
        return publishOutboxEvents
            .publishAvailable(
                new PublishOutboxEventsCommand(
                    "v016-outage-probe",
                    1,
                    Duration.ofSeconds(30)
                )
            );
    }

    private void insertOutboxEvent(
        UUID eventId,
        UUID transactionId,
        String payload
    ) {
        Instant now =
            Instant.now().minusSeconds(1);

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
            transactionId,
            "wallet.transfer.completed",
            1,
            SOURCE_TOPIC,
            transactionId.toString(),
            SOURCE_TOPIC
                + ":1:"
                + eventId,
            payload,
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private void insertReplayableDeadLetterRecord() {
        String replayPayload =
            payload(
                REPLAY_EVENT_ID,
                REPLAY_TX_ID,
                "2026-08-21T12:02:00Z"
            );

        jdbcTemplate.update(
            """
            INSERT INTO kafka_dead_letter_records (
                id,
                dlt_topic,
                dlt_partition,
                dlt_offset,
                original_topic,
                original_partition,
                original_offset,
                original_consumer_group,
                record_key,
                payload,
                exception_type,
                exception_message,
                status,
                replay_count,
                received_at,
                last_replayed_at,
                replay_lease_owner,
                replay_lease_until,
                last_replay_error,
                replay_origin_id,
                replay_attempt_base
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """,
            REPLAY_RECORD_ID,
            DLT_TOPIC,
            0,
            9000000L,
            SOURCE_TOPIC,
            0,
            8000000L,
            CONSUMER_NAME,
            REPLAY_TX_ID.toString(),
            replayPayload,
            "java.lang.IllegalStateException",
            "Synthetic transient processing failure.",
            "RECEIVED",
            0,
            Timestamp.from(
                Instant.now()
                    .minusSeconds(60)
            ),
            null,
            null,
            null,
            null,
            REPLAY_RECORD_ID,
            0
        );
    }

    private void assertOutboxState(
        UUID eventId,
        String expectedStatus,
        int expectedAttempts,
        boolean expectPublishedAt,
        boolean expectError
    ) {
        Map<String, Object> state =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    status,
                    attempt_count,
                    published_at,
                    last_error,
                    locked_at,
                    locked_until,
                    locked_by
                FROM outbox_events
                WHERE id = ?
                """,
                eventId
            );

        assertThat(state.get("status"))
            .isEqualTo(expectedStatus);

        assertThat(
            ((Number) state.get(
                "attempt_count"
            )).intValue()
        )
            .isEqualTo(expectedAttempts);

        if (expectPublishedAt) {
            assertThat(
                state.get("published_at")
            )
                .isNotNull();
        } else {
            assertThat(
                state.get("published_at")
            )
                .isNull();
        }

        if (expectError) {
            assertThat(
                state.get("last_error")
            )
                .asString()
                .isNotBlank();
        } else {
            assertThat(
                state.get("last_error")
            )
                .isNull();
        }

        assertThat(state.get("locked_at"))
            .isNull();
        assertThat(state.get("locked_until"))
            .isNull();
        assertThat(state.get("locked_by"))
            .isNull();
    }

    private void waitUntilOutboxAvailable(
        UUID eventId
    ) throws Exception {
        Timestamp availableAt =
            jdbcTemplate.queryForObject(
                """
                SELECT available_at
                FROM outbox_events
                WHERE id = ?
                """,
                Timestamp.class,
                eventId
            );

        assertThat(availableAt)
            .isNotNull();

        while (
            Instant.now().isBefore(
                availableAt.toInstant()
            )
        ) {
            TimeUnit.MILLISECONDS.sleep(100);
        }

        TimeUnit.MILLISECONDS.sleep(250);
    }

    private int processedCount(
        UUID eventId
    ) {
        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM processed_kafka_events
                WHERE consumer_name = ?
                  AND event_id = ?
                """,
                Integer.class,
                CONSUMER_NAME,
                eventId
            );

        return count == null ? 0 : count;
    }

    private int auditCount(
        UUID eventId
    ) {
        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transfer_completed_event_audits
                WHERE event_id = ?
                """,
                Integer.class,
                eventId
            );

        return count == null ? 0 : count;
    }

    private int dltRecordCountByKey(
        String recordKey
    ) {
        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM kafka_dead_letter_records
                WHERE record_key = ?
                """,
                Integer.class,
                recordKey
            );

        return count == null ? 0 : count;
    }

    private ReplayState replayState() {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                status,
                replay_count,
                replay_lease_owner,
                replay_lease_until,
                last_replay_error,
                replay_origin_id,
                replay_attempt_base
            FROM kafka_dead_letter_records
            WHERE id = ?
            """,
            (resultSet, rowNumber) ->
                new ReplayState(
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getInt(
                        "replay_count"
                    ),
                    resultSet.getString(
                        "replay_lease_owner"
                    ),
                    instant(
                        resultSet.getTimestamp(
                            "replay_lease_until"
                        )
                    ),
                    resultSet.getString(
                        "last_replay_error"
                    ),
                    resultSet.getObject(
                        "replay_origin_id",
                        UUID.class
                    ),
                    resultSet.getInt(
                        "replay_attempt_base"
                    )
                ),
            REPLAY_RECORD_ID
        );
    }

    private static Instant instant(
        Timestamp value
    ) {
        return value == null
            ? null
            : value.toInstant();
    }

    private static String payload(
        UUID eventId,
        UUID transactionId,
        String occurredAt
    ) {
        return """
            {
              "eventId": "%s",
              "eventType": "wallet.transfer.completed",
              "eventVersion": 1,
              "occurredAt": "%s",
              "transactionId": "%s",
              "sourceWalletId": "95000000-0000-0000-0000-000000000101",
              "targetWalletId": "95000000-0000-0000-0000-000000000102",
              "amount": "125.50",
              "currency": "TRY"
            }
            """.formatted(
                eventId,
                occurredAt,
                transactionId
            );
    }

    private static KafkaConsumer<String, String>
    observerConsumer() {
        Map<String, Object> properties =
            new HashMap<>();

        properties.put(
            ConsumerConfig
                .BOOTSTRAP_SERVERS_CONFIG,
            KAFKA.getBootstrapServers()
        );

        properties.put(
            ConsumerConfig.GROUP_ID_CONFIG,
            "v016-outage-observer-"
                + UUID.randomUUID()
        );

        properties.put(
            ConsumerConfig
                .AUTO_OFFSET_RESET_CONFIG,
            "earliest"
        );

        properties.put(
            ConsumerConfig
                .ENABLE_AUTO_COMMIT_CONFIG,
            false
        );

        return new KafkaConsumer<>(
            properties,
            new StringDeserializer(),
            new StringDeserializer()
        );
    }

    private static ConsumerRecord<String, String>
    awaitReplayAttempt(
        KafkaConsumer<String, String> consumer,
        UUID eventId,
        UUID replayOriginId,
        int expectedAttempt
    ) {
        String expectedEventId =
            eventId.toString();

        String expectedOriginId =
            replayOriginId.toString();

        long deadline =
            System.nanoTime()
                + Duration.ofSeconds(30)
                    .toNanos();

        boolean observedEarlierAttempt = false;

        while (
            System.nanoTime() < deadline
        ) {
            ConsumerRecords<String, String> records =
                consumer.poll(
                    Duration.ofMillis(250)
                );

            for (
                ConsumerRecord<String, String>
                    record : records
            ) {
                if (
                    record.value() == null
                        || !record.value()
                            .contains(
                                expectedEventId
                            )
                ) {
                    continue;
                }

                assertThat(
                    headerValue(
                        record,
                        KafkaDeadLetterReplayHeaders
                            .REPLAY_ORIGIN_ID
                    )
                )
                    .isEqualTo(
                        expectedOriginId
                    );

                int attempt =
                    Integer.parseInt(
                        headerValue(
                            record,
                            KafkaDeadLetterReplayHeaders
                                .REPLAY_ATTEMPT
                        )
                    );

                assertThat(attempt)
                    .isBetween(
                        1,
                        expectedAttempt
                    );

                if (attempt < expectedAttempt) {
                    observedEarlierAttempt = true;
                    continue;
                }

                return record;
            }
        }

        throw new AssertionError(
            "Timed out waiting for replay attempt "
                + expectedAttempt
                + " for Kafka event "
                + eventId
                + ". Earlier timed-out attempt "
                + "observed="
                + observedEarlierAttempt
        );
    }

    private static ConsumerRecord<String, String>
    awaitRecord(
        KafkaConsumer<String, String> consumer,
        UUID eventId
    ) {
        String expected =
            eventId.toString();

        long deadline =
            System.nanoTime()
                + Duration.ofSeconds(30)
                    .toNanos();

        while (
            System.nanoTime() < deadline
        ) {
            ConsumerRecords<String, String> records =
                consumer.poll(
                    Duration.ofMillis(250)
                );

            for (
                ConsumerRecord<String, String>
                    record : records
            ) {
                if (
                    record.value() != null
                        && record.value()
                            .contains(expected)
                ) {
                    return record;
                }
            }
        }

        throw new AssertionError(
            "Timed out waiting for Kafka event "
                + eventId
        );
    }

    private static String headerValue(
        ConsumerRecord<String, String> record,
        String headerName
    ) {
        Header header =
            record.headers()
                .lastHeader(headerName);

        assertThat(header)
            .isNotNull();

        return new String(
            header.value(),
            UTF_8
        );
    }

    private void assertJsonEquivalent(
        String actual,
        String expected
    ) throws Exception {

        JsonNode actualJson =
            objectMapper.readTree(actual);

        JsonNode expectedJson =
            objectMapper.readTree(expected);

        assertThat(actualJson)
            .isEqualTo(expectedJson);
    }

    private void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM kafka_dead_letter_command_audits"
        );
        jdbcTemplate.update(
            "DELETE FROM kafka_dead_letter_records"
        );
        jdbcTemplate.update(
            "DELETE FROM transfer_completed_event_audits"
        );
        jdbcTemplate.update(
            "DELETE FROM processed_kafka_events"
        );
        jdbcTemplate.update(
            "DELETE FROM outbox_events"
        );
    }

    private int count(String table) {
        Integer value =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Integer.class
            );

        return value == null ? 0 : value;
    }

    private static void pauseKafka() {
        dockerClient()
            .pauseContainerCmd(
                KAFKA.getContainerId()
            )
            .exec();
    }

    private static void unpauseKafka() {
        dockerClient()
            .unpauseContainerCmd(
                KAFKA.getContainerId()
            )
            .exec();
    }

    private static void awaitKafka()
        throws Exception {

        long deadline =
            System.nanoTime()
                + Duration.ofSeconds(60)
                    .toNanos();

        Exception lastFailure = null;

        while (
            System.nanoTime() < deadline
        ) {
            try (
                Admin admin = Admin.create(
                    Map.of(
                        AdminClientConfig
                            .BOOTSTRAP_SERVERS_CONFIG,
                        KAFKA.getBootstrapServers()
                    )
                )
            ) {
                if (
                    admin.listTopics()
                        .names()
                        .get(
                            5,
                            TimeUnit.SECONDS
                        )
                        .contains(SOURCE_TOPIC)
                ) {
                    return;
                }
            } catch (Exception exception) {
                lastFailure = exception;
            }

            TimeUnit.MILLISECONDS.sleep(500);
        }

        throw new AssertionError(
            "Kafka did not recover in time.",
            lastFailure
        );
    }

    private static void await(
        BooleanSupplier condition,
        String description
    ) throws Exception {

        long deadline =
            System.nanoTime()
                + Duration.ofSeconds(30)
                    .toNanos();

        Throwable lastFailure = null;

        while (
            System.nanoTime() < deadline
        ) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (Throwable failure) {
                lastFailure = failure;
            }

            TimeUnit.MILLISECONDS.sleep(200);
        }

        throw new AssertionError(
            "Timed out waiting for "
                + description,
            lastFailure
        );
    }

    private static DockerClient dockerClient() {
        return DockerClientFactory
            .instance()
            .client();
    }

    private record ReplayState(
        String status,
        int replayCount,
        String leaseOwner,
        Instant leaseUntil,
        String lastError,
        UUID originId,
        int attemptBase
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaTopicConfiguration {

        @Bean
        NewTopic sourceTopic() {
            return TopicBuilder
                .name(SOURCE_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
        }

        @Bean
        NewTopic deadLetterTopic() {
            return TopicBuilder
                .name(DLT_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
        }
    }
}
