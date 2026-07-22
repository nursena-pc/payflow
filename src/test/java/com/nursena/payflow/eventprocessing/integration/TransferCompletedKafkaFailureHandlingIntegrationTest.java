package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.nursena.payflow.eventprocessing.application.port.out.TransferCompletedEventHandlerPort;
import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(
    properties = {
        "payflow.event-processing"
            + ".transfer-completed.enabled=true",
        "payflow.event-processing"
            + ".transfer-completed.topic="
            + "wallet.transfer.completed"
            + ".failure-integration",
        "payflow.event-processing"
            + ".transfer-completed.group-id="
            + "transfer-completed-failure"
            + "-integration-test",
        "payflow.event-processing"
            + ".transfer-completed.consumer-name="
            + "transfer-completed-audit-failure"
            + "-integration-test",
        "payflow.event-processing"
            + ".transfer-completed.failure"
            + ".dead-letter-topic="
            + "wallet.transfer.completed"
            + ".failure-integration.dlt",
        "payflow.event-processing"
            + ".transfer-completed.failure"
            + ".max-retries=2",
        "payflow.event-processing"
            + ".transfer-completed.failure"
            + ".initial-delay=10ms",
        "payflow.event-processing"
            + ".transfer-completed.failure"
            + ".multiplier=1.0",
        "payflow.event-processing"
            + ".transfer-completed.failure"
            + ".maximum-delay=10ms",
        "payflow.event-processing"
            + ".transfer-completed.failure"
            + ".send-timeout=10s",
        "spring.kafka.consumer"
            + ".enable-auto-commit=false",
        "spring.kafka.consumer"
            + ".auto-offset-reset=earliest",
        "spring.kafka.listener.ack-mode=record"
    }
)
@Testcontainers
@Import(
    TransferCompletedKafkaFailureHandlingIntegrationTest
        .KafkaTopicTestConfiguration.class
)
class TransferCompletedKafkaFailureHandlingIntegrationTest {

    private static final String TOPIC =
        "wallet.transfer.completed"
            + ".failure-integration";

    private static final String DEAD_LETTER_TOPIC =
        TOPIC + ".dlt";

    private static final String CONSUMER_GROUP =
        "transfer-completed-failure"
            + "-integration-test";

    private static final String
        DELIVERY_FAILURES_METRIC =
        "payflow.kafka.consumer.delivery.failures";

    private static final String
        RETRY_ATTEMPTS_METRIC =
        "payflow.kafka.consumer.retry.attempts";

    private static final String
        RECOVERIES_METRIC =
        "payflow.kafka.consumer.recoveries";

    private static final String CONSUMER_NAME =
        "transfer-completed-audit-failure"
            + "-integration-test";

    private static final String DLT_INSPECTION_GROUP =
        "transfer-completed-dlt-inspection"
            + "-integration-test";

    private static final UUID MALFORMED_TRANSACTION_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000001301"
        );

    private static final UUID RETRY_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000001302"
        );

    private static final UUID RETRY_TRANSACTION_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000001302"
        );

    private static final String MALFORMED_PAYLOAD =
        "{invalid-json";

    private static final String VALID_PAYLOAD = """
        {
          "eventId": "50000000-0000-0000-0000-000000001302",
          "eventType": "wallet.transfer.completed",
          "eventVersion": 1,
          "occurredAt": "2026-07-21T13:00:00Z",
          "transactionId": "60000000-0000-0000-0000-000000001302",
          "sourceWalletId": "70000000-0000-0000-0000-000000001301",
          "targetWalletId": "70000000-0000-0000-0000-000000001302",
          "amount": "125.50",
          "currency": "TRY"
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

    @MockitoBean(
        name = "transferCompletedAuditHandler"
    )
    private TransferCompletedEventHandlerPort
        eventHandler;

    @Autowired
    private KafkaTemplate<String, String>
        kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldRecoverPermanentAndExhaustedRetryableFailures()
        throws Exception {

        cleanDatabase();
        clearFailureMetrics();

        try (
            Consumer<String, String> dltConsumer =
                createDeadLetterConsumer()
        ) {
            dltConsumer.subscribe(
                List.of(DEAD_LETTER_TOPIC)
            );

            verifyMalformedJsonRecovery(
                dltConsumer
            );

            reset(eventHandler);

            verifyRetryExhaustionRecovery(
                dltConsumer
            );
        }
    }

    private void verifyMalformedJsonRecovery(
        Consumer<String, String> dltConsumer
    ) throws Exception {

        long sourceOffset = publish(
            MALFORMED_TRANSACTION_ID.toString(),
            MALFORMED_PAYLOAD
        );

        ConsumerRecord<String, String> deadLetterRecord =
            KafkaTestUtils.getSingleRecord(
                dltConsumer,
                DEAD_LETTER_TOPIC,
                java.time.Duration.ofSeconds(20)
            );

        awaitCommittedOffset(
            sourceOffset + 1
        );

        assertDeadLetterRecord(
            deadLetterRecord,
            MALFORMED_TRANSACTION_ID.toString(),
            MALFORMED_PAYLOAD,
            sourceOffset,
            "TransferCompletedEventDeserializationException"
        );

        verifyNoInteractions(
            eventHandler
        );

        assertDatabaseRemainsEmpty();
        assertMalformedJsonMetrics();
    }

    private void verifyRetryExhaustionRecovery(
        Consumer<String, String> dltConsumer
    ) throws Exception {

        doThrow(
            new IllegalStateException(
                "temporary handler failure"
            )
        )
            .when(eventHandler)
            .handle(
                any(TransferCompletedEvent.class)
            );

        long sourceOffset = publish(
            RETRY_TRANSACTION_ID.toString(),
            VALID_PAYLOAD
        );

        ConsumerRecord<String, String> deadLetterRecord =
            KafkaTestUtils.getSingleRecord(
                dltConsumer,
                DEAD_LETTER_TOPIC,
                java.time.Duration.ofSeconds(20)
            );

        awaitCommittedOffset(
            sourceOffset + 1
        );

        /*
         * Initial delivery plus two configured retries.
         */
        verify(
            eventHandler,
            times(3)
        )
            .handle(
                any(TransferCompletedEvent.class)
            );

        assertDeadLetterRecord(
            deadLetterRecord,
            RETRY_TRANSACTION_ID.toString(),
            VALID_PAYLOAD,
            sourceOffset,
            IllegalStateException.class.getName()
        );

        assertDatabaseRemainsEmpty();
        assertRetryExhaustionMetrics();
    }

    private long publish(
        String key,
        String payload
    ) throws Exception {

        return kafkaTemplate.send(
                TOPIC,
                key,
                payload
            )
            .get(
                20,
                TimeUnit.SECONDS
            )
            .getRecordMetadata()
            .offset();
    }

    private static Consumer<String, String>
    createDeadLetterConsumer() {

        Map<String, Object> properties =
            new HashMap<>();

        properties.put(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KAFKA.getBootstrapServers()
        );

        properties.put(
            ConsumerConfig.GROUP_ID_CONFIG,
            DLT_INSPECTION_GROUP
        );

        properties.put(
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            false
        );

        properties.put(
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest"
        );

        properties.put(
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class
        );

        properties.put(
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class
        );

        return new KafkaConsumer<>(
            properties
        );
    }

    private static void assertDeadLetterRecord(
        ConsumerRecord<String, String> record,
        String expectedKey,
        String expectedPayload,
        long expectedOriginalOffset,
        String expectedExceptionText
    ) {
        assertThat(record.topic())
            .isEqualTo(
                DEAD_LETTER_TOPIC
            );

        assertThat(record.partition())
            .isZero();

        assertThat(record.key())
            .isEqualTo(expectedKey);

        assertThat(record.value())
            .isEqualTo(expectedPayload);

        assertThat(
            stringHeader(
                record,
                KafkaHeaders.DLT_ORIGINAL_TOPIC
            )
        )
            .isEqualTo(TOPIC);

        assertThat(
            integerHeader(
                record,
                KafkaHeaders.DLT_ORIGINAL_PARTITION
            )
        )
            .isZero();

        assertThat(
            longHeader(
                record,
                KafkaHeaders.DLT_ORIGINAL_OFFSET
            )
        )
            .isEqualTo(
                expectedOriginalOffset
            );

        assertThat(
            stringHeader(
                record,
                KafkaHeaders
                    .DLT_ORIGINAL_CONSUMER_GROUP
            )
        )
            .isEqualTo(
                CONSUMER_GROUP
            );

        assertThat(
            stringHeader(
                record,
                KafkaHeaders.DLT_EXCEPTION_STACKTRACE
            )
        )
            .contains(
                expectedExceptionText
            );
    }

    private static String stringHeader(
        ConsumerRecord<String, String> record,
        String headerName
    ) {
        return new String(
            requiredHeaderValue(
                record,
                headerName
            ),
            StandardCharsets.UTF_8
        );
    }

    private static int integerHeader(
        ConsumerRecord<String, String> record,
        String headerName
    ) {
        return ByteBuffer.wrap(
                requiredHeaderValue(
                    record,
                    headerName
                )
            )
            .getInt();
    }

    private static long longHeader(
        ConsumerRecord<String, String> record,
        String headerName
    ) {
        return ByteBuffer.wrap(
                requiredHeaderValue(
                    record,
                    headerName
                )
            )
            .getLong();
    }

    private static byte[] requiredHeaderValue(
        ConsumerRecord<String, String> record,
        String headerName
    ) {
        Header header =
            record.headers()
                .lastHeader(headerName);

        assertThat(header)
            .as(
                "Kafka header %s",
                headerName
            )
            .isNotNull();

        return header.value();
    }

    private void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM transfer_completed_event_audits"
        );

        jdbcTemplate.update(
            "DELETE FROM processed_kafka_events"
        );
    }

    private void assertDatabaseRemainsEmpty() {
        Long processedEventCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM processed_kafka_events
                WHERE consumer_name = ?
                """,
                Long.class,
                CONSUMER_NAME
            );

        Long auditCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transfer_completed_event_audits
                WHERE event_id = ?
                   OR transaction_id = ?
                """,
                Long.class,
                RETRY_EVENT_ID,
                RETRY_TRANSACTION_ID
            );

        assertThat(processedEventCount)
            .isZero();

        assertThat(auditCount)
            .isZero();
    }

    private void assertMalformedJsonMetrics() {
        assertCounterTotal(
            DELIVERY_FAILURES_METRIC,
            1.0,
            "consumer",
            CONSUMER_NAME,
            "topic",
            TOPIC,
            "failure_type",
            "permanent"
        );

        assertCounterTotal(
            RETRY_ATTEMPTS_METRIC,
            0.0,
            "consumer",
            CONSUMER_NAME,
            "topic",
            TOPIC,
            "failure_type",
            "permanent"
        );

        assertCounterTotal(
            RECOVERIES_METRIC,
            1.0,
            "consumer",
            CONSUMER_NAME,
            "topic",
            TOPIC,
            "failure_type",
            "permanent",
            "outcome",
            "success"
        );

        assertCounterTotal(
            RECOVERIES_METRIC,
            0.0,
            "consumer",
            CONSUMER_NAME,
            "topic",
            TOPIC,
            "failure_type",
            "permanent",
            "outcome",
            "failure"
        );
    }

    private void assertRetryExhaustionMetrics() {
        assertCounterTotal(
            DELIVERY_FAILURES_METRIC,
            3.0,
            "consumer",
            CONSUMER_NAME,
            "topic",
            TOPIC,
            "failure_type",
            "retryable"
        );

        assertCounterTotal(
            RETRY_ATTEMPTS_METRIC,
            2.0,
            "consumer",
            CONSUMER_NAME,
            "topic",
            TOPIC,
            "failure_type",
            "retryable"
        );

        assertCounterTotal(
            RECOVERIES_METRIC,
            1.0,
            "consumer",
            CONSUMER_NAME,
            "topic",
            TOPIC,
            "failure_type",
            "retryable",
            "outcome",
            "success"
        );

        assertCounterTotal(
            RECOVERIES_METRIC,
            0.0,
            "consumer",
            CONSUMER_NAME,
            "topic",
            TOPIC,
            "failure_type",
            "retryable",
            "outcome",
            "failure"
        );
    }

    private void assertCounterTotal(
        String metricName,
        double expected,
        String... tags
    ) {
        double actual =
            meterRegistry.find(metricName)
                .tags(tags)
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();

        assertThat(actual)
            .as(
                "counter total for %s with tags %s",
                metricName,
                java.util.Arrays.toString(tags)
            )
            .isEqualTo(expected);
    }

    private void clearFailureMetrics() {
        meterRegistry.getMeters()
            .stream()
            .filter(
                meter ->
                    meter.getId()
                        .getName()
                        .startsWith(
                            "payflow.kafka.consumer."
                        )
            )
            .toList()
            .forEach(meterRegistry::remove);
    }

    private static void awaitCommittedOffset(
        long expectedOffset
    ) throws Exception {

        TopicPartition topicPartition =
            new TopicPartition(
                TOPIC,
                0
            );

        long deadline =
            System.nanoTime()
                + TimeUnit.SECONDS.toNanos(20);

        long latestCommittedOffset = -1L;

        try (
            Admin admin = Admin.create(
                Map.of(
                    AdminClientConfig
                        .BOOTSTRAP_SERVERS_CONFIG,
                    KAFKA.getBootstrapServers()
                )
            )
        ) {
            while (
                System.nanoTime() < deadline
            ) {
                Map<TopicPartition, OffsetAndMetadata>
                    committedOffsets =
                    admin
                        .listConsumerGroupOffsets(
                            CONSUMER_GROUP
                        )
                        .partitionsToOffsetAndMetadata()
                        .get(
                            5,
                            TimeUnit.SECONDS
                        );

                OffsetAndMetadata metadata =
                    committedOffsets.get(
                        topicPartition
                    );

                if (metadata != null) {
                    latestCommittedOffset =
                        metadata.offset();

                    if (latestCommittedOffset
                        >= expectedOffset) {

                        return;
                    }
                }

                Thread.sleep(100);
            }
        }

        assertThat(latestCommittedOffset)
            .as(
                "committed offset for %s",
                topicPartition
            )
            .isGreaterThanOrEqualTo(
                expectedOffset
            );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaTopicTestConfiguration {

        @Bean
        NewTopic transferCompletedFailureTopic() {
            return TopicBuilder
                .name(TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
        }

        @Bean
        NewTopic transferCompletedDeadLetterTopic() {
            return TopicBuilder
                .name(DEAD_LETTER_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
        }
    }
}
