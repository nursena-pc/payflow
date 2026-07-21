package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(
    properties = {
        "payflow.event-processing"
            + ".transfer-completed.enabled=false",
        "payflow.event-processing"
            + ".transfer-completed.failure"
            + ".dead-letter-topic="
            + "wallet.transfer.completed"
            + ".dlt-intake-integration",
        "payflow.event-processing"
            + ".transfer-completed"
            + ".dead-letter-intake.enabled=true",
        "payflow.event-processing"
            + ".transfer-completed"
            + ".dead-letter-intake.group-id="
            + "transfer-completed-dlt-intake"
            + "-integration-test",
        "spring.kafka.consumer"
            + ".enable-auto-commit=false",
        "spring.kafka.consumer"
            + ".auto-offset-reset=earliest",
        "spring.kafka.listener.ack-mode=record"
    }
)
@Testcontainers
@Import(
    TransferCompletedKafkaDeadLetterIntakeIntegrationTest
        .KafkaTopicTestConfiguration.class
)
class TransferCompletedKafkaDeadLetterIntakeIntegrationTest {

    private static final String DEAD_LETTER_TOPIC =
        "wallet.transfer.completed"
            + ".dlt-intake-integration";

    private static final String ORIGINAL_TOPIC =
        "wallet.transfer.completed"
            + ".integration-source";

    private static final String ORIGINAL_GROUP =
        "transfer-completed-source"
            + "-integration-test";

    private static final String INTAKE_GROUP =
        "transfer-completed-dlt-intake"
            + "-integration-test";

    private static final String LISTENER_ID =
        "transferCompletedKafkaDeadLetter"
            + "IntakeListener";

    private static final TopicPartition
        DEAD_LETTER_PARTITION =
        new TopicPartition(
            DEAD_LETTER_TOPIC,
            0
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
    private KafkaTemplate<String, String>
        kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry
        listenerRegistry;

    @Test
    void shouldPersistRedeliveryAndStopWithoutSkippingInvalidRecord()
        throws Exception {

        cleanDatabase();

        MessageListenerContainer container =
            listenerRegistry.getListenerContainer(
                LISTENER_ID
            );

        assertThat(container)
            .isNotNull();

        await(
            container::isRunning,
            "DLT intake container to start"
        );

        long validOffset =
            publish(
                validRecord()
            );

        await(
            () -> countRecords() == 1,
            "valid DLT record persistence"
        );

        awaitCommittedOffset(
            validOffset + 1
        );

        assertStoredRecord(
            validOffset
        );

        /*
         * Rewind the consumer group so that Kafka
         * redelivers the same physical DLT record.
         */
        container.stop();

        await(
            () -> !container.isRunning(),
            "DLT intake container to stop"
        );

        alterCommittedOffset(
            validOffset
        );

        assertThat(committedOffset())
            .isEqualTo(validOffset);

        container.start();

        await(
            container::isRunning,
            "DLT intake container to restart"
        );

        awaitCommittedOffset(
            validOffset + 1
        );

        assertThat(countRecords())
            .isEqualTo(1);

        /*
         * The next record lacks a required header.
         * The dedicated stopping error handler must
         * stop the container without committing it.
         */
        long committedBeforeInvalid =
            committedOffset();

        long invalidOffset =
            publish(
                recordWithoutOriginalOffset()
            );

        assertThat(invalidOffset)
            .isEqualTo(
                committedBeforeInvalid
            );

        await(
            () -> !container.isRunning(),
            "DLT intake container to stop "
                + "after invalid metadata"
        );

        assertThat(committedOffset())
            .isEqualTo(
                committedBeforeInvalid
            );

        assertThat(countRecords())
            .isEqualTo(1);
    }

    private ProducerRecord<String, String>
    validRecord() {
        ProducerRecord<String, String> record =
            new ProducerRecord<>(
                DEAD_LETTER_TOPIC,
                0,
                "transaction-id",
                "{invalid-json"
            );

        addRequiredHeaders(record);

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders.DLT_EXCEPTION_MESSAGE,
                    bytes(
                        "Temporary processing failure."
                    )
                )
            );

        return record;
    }

    private ProducerRecord<String, String>
    recordWithoutOriginalOffset() {
        ProducerRecord<String, String> record =
            new ProducerRecord<>(
                DEAD_LETTER_TOPIC,
                0,
                "invalid-transaction-id",
                "invalid-payload"
            );

        addRequiredHeaders(record);

        record.headers()
            .remove(
                KafkaHeaders.DLT_ORIGINAL_OFFSET
            );

        return record;
    }

    private static void addRequiredHeaders(
        ProducerRecord<String, String> record
    ) {
        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders.DLT_ORIGINAL_TOPIC,
                    bytes(ORIGINAL_TOPIC)
                )
            );

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders
                        .DLT_ORIGINAL_PARTITION,
                    integerBytes(1)
                )
            );

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders.DLT_ORIGINAL_OFFSET,
                    longBytes(42L)
                )
            );

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders
                        .DLT_ORIGINAL_CONSUMER_GROUP,
                    bytes(ORIGINAL_GROUP)
                )
            );

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders.DLT_EXCEPTION_FQCN,
                    bytes(
                        "java.lang.IllegalStateException"
                    )
                )
            );
    }

    private long publish(
        ProducerRecord<String, String> record
    ) throws Exception {

        return kafkaTemplate
            .send(record)
            .get(
                10,
                TimeUnit.SECONDS
            )
            .getRecordMetadata()
            .offset();
    }

    private void assertStoredRecord(
        long deadLetterOffset
    ) {
        Map<String, Object> stored =
            jdbcTemplate.queryForMap(
                """
                SELECT
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
                    replay_count
                FROM kafka_dead_letter_records
                WHERE dlt_topic = ?
                  AND dlt_partition = ?
                  AND dlt_offset = ?
                """,
                DEAD_LETTER_TOPIC,
                0,
                deadLetterOffset
            );

        assertThat(
            stored.get("dlt_topic")
        )
            .isEqualTo(
                DEAD_LETTER_TOPIC
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
            .isEqualTo(deadLetterOffset);

        assertThat(
            stored.get("original_topic")
        )
            .isEqualTo(ORIGINAL_TOPIC);

        assertThat(
            stored.get("original_partition")
        )
            .isEqualTo(1);

        assertThat(
            ((Number) stored.get(
                "original_offset"
            )).longValue()
        )
            .isEqualTo(42L);

        assertThat(
            stored.get("original_consumer_group")
        )
            .isEqualTo(ORIGINAL_GROUP);

        assertThat(
            stored.get("record_key")
        )
            .isEqualTo("transaction-id");

        assertThat(
            stored.get("payload")
        )
            .isEqualTo("{invalid-json");

        assertThat(
            stored.get("exception_type")
        )
            .isEqualTo(
                "java.lang.IllegalStateException"
            );

        assertThat(
            stored.get("exception_message")
        )
            .isEqualTo(
                "Temporary processing failure."
            );

        assertThat(
            stored.get("status")
        )
            .isEqualTo("RECEIVED");

        assertThat(
            stored.get("replay_count")
        )
            .isEqualTo(0);
    }

    private void alterCommittedOffset(
        long offset
    ) throws Exception {

        try (
            Admin admin =
                Admin.create(
                    Map.of(
                        AdminClientConfig
                            .BOOTSTRAP_SERVERS_CONFIG,
                        KAFKA.getBootstrapServers()
                    )
                )
        ) {
            admin.alterConsumerGroupOffsets(
                    INTAKE_GROUP,
                    Map.of(
                        DEAD_LETTER_PARTITION,
                        new OffsetAndMetadata(offset)
                    )
                )
                .all()
                .get(
                    10,
                    TimeUnit.SECONDS
                );
        }
    }

    private long committedOffset()
        throws Exception {

        try (
            Admin admin =
                Admin.create(
                    Map.of(
                        AdminClientConfig
                            .BOOTSTRAP_SERVERS_CONFIG,
                        KAFKA.getBootstrapServers()
                    )
                )
        ) {
            OffsetAndMetadata metadata =
                admin.listConsumerGroupOffsets(
                        INTAKE_GROUP
                    )
                    .partitionsToOffsetAndMetadata()
                    .get(
                        10,
                        TimeUnit.SECONDS
                    )
                    .get(
                        DEAD_LETTER_PARTITION
                    );

            if (metadata == null) {
                return -1L;
            }

            return metadata.offset();
        }
    }

    private void awaitCommittedOffset(
        long expectedOffset
    ) throws Exception {

        await(
            () ->
                committedOffset()
                    == expectedOffset,
            "committed DLT offset "
                + expectedOffset
        );
    }

    private int countRecords() {
        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM kafka_dead_letter_records
                """,
                Integer.class
            );

        return count == null
            ? 0
            : count;
    }

    private void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM kafka_dead_letter_records"
        );
    }

    private static byte[] bytes(
        String value
    ) {
        return value.getBytes(
            StandardCharsets.UTF_8
        );
    }

    private static byte[] integerBytes(
        int value
    ) {
        return ByteBuffer
            .allocate(Integer.BYTES)
            .putInt(value)
            .array();
    }

    private static byte[] longBytes(
        long value
    ) {
        return ByteBuffer
            .allocate(Long.BYTES)
            .putLong(value)
            .array();
    }

    private static void await(
        CheckedCondition condition,
        String description
    ) throws Exception {

        long deadline =
            System.nanoTime()
                + Duration.ofSeconds(20)
                .toNanos();

        Throwable lastFailure = null;

        while (
            System.nanoTime()
                < deadline
        ) {
            try {
                if (condition.isSatisfied()) {
                    return;
                }
            } catch (Throwable failure) {
                lastFailure = failure;
            }

            Thread.sleep(100);
        }

        AssertionError timeout =
            new AssertionError(
                "Timed out waiting for "
                    + description
                    + "."
            );

        if (lastFailure != null) {
            timeout.initCause(
                lastFailure
            );
        }

        throw timeout;
    }

    @FunctionalInterface
    private interface CheckedCondition {

        boolean isSatisfied()
            throws Exception;
    }

    @TestConfiguration(
        proxyBeanMethods = false
    )
    static class KafkaTopicTestConfiguration {

        @Bean
        NewTopic deadLetterIntakeTopic() {
            return TopicBuilder
                .name(DEAD_LETTER_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
        }
    }
}
