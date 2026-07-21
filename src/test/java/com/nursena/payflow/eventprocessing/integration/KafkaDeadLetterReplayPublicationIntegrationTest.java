package com.nursena.payflow.eventprocessing.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.adapter.kafka.KafkaDeadLetterReplayHeaders;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.in.ReplayKafkaDeadLetterRecordUseCase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(
    properties = {
        "payflow.event-processing"
            + ".transfer-completed.enabled=false",
        "payflow.event-processing"
            + ".transfer-completed"
            + ".dead-letter-intake.enabled=false",
        "payflow.event-processing"
            + ".transfer-completed"
            + ".dead-letter-replay.worker-id="
            + "replay-publication-integration-worker",
        "payflow.event-processing"
            + ".transfer-completed"
            + ".dead-letter-replay.lease-duration=1m",
        "payflow.event-processing"
            + ".transfer-completed"
            + ".dead-letter-replay.max-attempts=3",
        "payflow.event-processing"
            + ".transfer-completed"
            + ".dead-letter-replay.send-timeout=10s"
    }
)
@Testcontainers
@Import(
    KafkaDeadLetterReplayPublicationIntegrationTest
        .KafkaTopicTestConfiguration.class
)
class KafkaDeadLetterReplayPublicationIntegrationTest {

    private static final String ORIGINAL_TOPIC =
        "wallet.transfer.completed"
            + ".replay-publication-integration";

    private static final String DEAD_LETTER_TOPIC =
        ORIGINAL_TOPIC + ".dlt";

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002101"
        );

    private static final UUID REPLAY_ORIGIN_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002100"
        );

    private static final String RECORD_KEY =
        "replayed-transaction-id";

    private static final String PAYLOAD = """
        {
          "eventId":
            "80000000-0000-0000-0000-000000002102",
          "eventType":
            "wallet.transfer.completed",
          "eventVersion": 1
        }
        """;

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
    private ReplayKafkaDeadLetterRecordUseCase
        replayUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM kafka_dead_letter_records"
        );
    }

    @Test
    void shouldClaimPublishAndMarkRecordAsReplayed() {
        insertReplayDerivedRecord();

        try (
            KafkaConsumer<String, String> consumer =
                replayConsumer()
        ) {
            consumer.subscribe(
                List.of(ORIGINAL_TOPIC)
            );

            ReplayKafkaDeadLetterRecordResult result =
                replayUseCase.replay(
                    new ReplayKafkaDeadLetterRecordCommand(
                        RECORD_ID
                    )
                );

            assertThat(result.isReplayed())
                .isTrue();

            ConsumerRecord<String, String> published =
                awaitPublishedRecord(consumer);

            assertThat(published.topic())
                .isEqualTo(ORIGINAL_TOPIC);

            assertThat(published.key())
                .isEqualTo(RECORD_KEY);

            assertThat(published.value())
                .isEqualTo(PAYLOAD);

            Header[] headers =
                published.headers()
                    .toArray();

            /*
             * A new ProducerRecord is used for replay,
             * so no Spring Kafka DLT headers are copied.
             */
            assertThat(headers)
                .extracting(Header::key)
                .containsExactly(
                    KafkaDeadLetterReplayHeaders
                        .REPLAY_ORIGIN_ID,
                    KafkaDeadLetterReplayHeaders
                        .REPLAY_ATTEMPT
                );

            assertThat(
                headerValue(
                    published,
                    KafkaDeadLetterReplayHeaders
                        .REPLAY_ORIGIN_ID
                )
            )
                .isEqualTo(
                    REPLAY_ORIGIN_ID.toString()
                );

            /*
             * The stored attempt base is 2. Claiming
             * increments replay_count from 0 to 1,
             * producing a chain-wide attempt value of 3.
             */
            assertThat(
                headerValue(
                    published,
                    KafkaDeadLetterReplayHeaders
                        .REPLAY_ATTEMPT
                )
            )
                .isEqualTo("3");

            assertNoAdditionalPublishedRecord(
                consumer
            );
        }

        ReplayState state =
            stateOfRecord();

        assertThat(state.status())
            .isEqualTo("REPLAYED");

        assertThat(state.replayCount())
            .isEqualTo(1);

        assertThat(state.lastReplayedAt())
            .isNotNull();

        assertThat(state.replayLeaseOwner())
            .isNull();

        assertThat(state.replayLeaseUntil())
            .isNull();

        assertThat(state.lastReplayError())
            .isNull();

        assertThat(state.replayOriginId())
            .isEqualTo(REPLAY_ORIGIN_ID);

        assertThat(state.replayAttemptBase())
            .isEqualTo(2);
    }

    private void insertReplayDerivedRecord() {
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
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?
            )
            """,
            RECORD_ID,
            DEAD_LETTER_TOPIC,
            0,
            25L,
            ORIGINAL_TOPIC,
            0,
            10L,
            "payflow-transfer-completed-audit-v1",
            RECORD_KEY,
            PAYLOAD,
            "java.lang.IllegalStateException",
            "Temporary processing failure.",
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
            REPLAY_ORIGIN_ID,
            2
        );
    }

    private static KafkaConsumer<String, String>
    replayConsumer() {
        Map<String, Object> properties =
            new HashMap<>();

        properties.put(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KAFKA.getBootstrapServers()
        );

        properties.put(
            ConsumerConfig.GROUP_ID_CONFIG,
            "replay-publication-integration-"
                + UUID.randomUUID()
        );

        properties.put(
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest"
        );

        properties.put(
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            false
        );

        return new KafkaConsumer<>(
            properties,
            new StringDeserializer(),
            new StringDeserializer()
        );
    }

    private static ConsumerRecord<String, String>
    awaitPublishedRecord(
        KafkaConsumer<String, String> consumer
    ) {
        long deadline =
            System.nanoTime()
                + Duration.ofSeconds(20)
                .toNanos();

        while (
            System.nanoTime()
                < deadline
        ) {
            ConsumerRecords<String, String> records =
                consumer.poll(
                    Duration.ofMillis(250)
                );

            if (!records.isEmpty()) {
                assertThat(records.count())
                    .isEqualTo(1);

                return records.iterator()
                    .next();
            }
        }

        throw new AssertionError(
            "Timed out waiting for replayed "
                + "Kafka record."
        );
    }

    private static void
    assertNoAdditionalPublishedRecord(
        KafkaConsumer<String, String> consumer
    ) {
        ConsumerRecords<String, String> additional =
            consumer.poll(
                Duration.ofMillis(500)
            );

        assertThat(additional)
            .isEmpty();
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

    private ReplayState stateOfRecord() {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                status,
                replay_count,
                last_replayed_at,
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
                    instant(
                        resultSet.getTimestamp(
                            "last_replayed_at"
                        )
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
            RECORD_ID
        );
    }

    private static Instant instant(
        Timestamp timestamp
    ) {
        return timestamp == null
            ? null
            : timestamp.toInstant();
    }

    private record ReplayState(
        String status,
        int replayCount,
        Instant lastReplayedAt,
        String replayLeaseOwner,
        Instant replayLeaseUntil,
        String lastReplayError,
        UUID replayOriginId,
        int replayAttemptBase
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaTopicTestConfiguration {

        @Bean
        NewTopic replayPublicationTopic() {
            return TopicBuilder
                .name(ORIGINAL_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
        }
    }
}
