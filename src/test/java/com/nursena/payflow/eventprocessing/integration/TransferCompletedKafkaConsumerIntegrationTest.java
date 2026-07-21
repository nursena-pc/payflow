package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
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
import org.springframework.kafka.core.KafkaTemplate;
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
            + ".consumer-integration",
        "payflow.event-processing"
            + ".transfer-completed.group-id="
            + "transfer-completed-consumer"
            + "-integration-test",
        "payflow.event-processing"
            + ".transfer-completed.consumer-name="
            + "transfer-completed-audit"
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
    TransferCompletedKafkaConsumerIntegrationTest
        .KafkaTopicTestConfiguration.class
)
class TransferCompletedKafkaConsumerIntegrationTest {

    private static final String TOPIC =
        "wallet.transfer.completed"
            + ".consumer-integration";

    private static final String CONSUMER_GROUP =
        "transfer-completed-consumer"
            + "-integration-test";

    private static final String CONSUMER_NAME =
        "transfer-completed-audit"
            + "-integration-test";

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000001201"
        );

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000001201"
        );

    private static final String PAYLOAD = """
        {
          "eventId": "50000000-0000-0000-0000-000000001201",
          "eventType": "wallet.transfer.completed",
          "eventVersion": 1,
          "occurredAt": "2026-07-21T12:00:00Z",
          "transactionId": "60000000-0000-0000-0000-000000001201",
          "sourceWalletId": "70000000-0000-0000-0000-000000001201",
          "targetWalletId": "70000000-0000-0000-0000-000000001202",
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

    @Autowired
    private KafkaTemplate<String, String>
        kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM transfer_completed_event_audits"
        );

        jdbcTemplate.update(
            "DELETE FROM processed_kafka_events"
        );
    }

    @Test
    void shouldConsumeDuplicateDeliveryOnlyOnce()
        throws Exception {

        publishEvent();

        awaitCommittedOffset(1L);

        assertStoredCounts();

        publishEvent();

        awaitCommittedOffset(2L);

        assertStoredCounts();

        Map<String, Object> processedEvent =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    event_type,
                    event_version,
                    topic,
                    partition_number,
                    record_offset
                FROM processed_kafka_events
                WHERE consumer_name = ?
                  AND event_id = ?
                """,
                CONSUMER_NAME,
                EVENT_ID
            );

        assertThat(
            processedEvent.get("event_type")
        )
            .isEqualTo(
                "wallet.transfer.completed"
            );

        assertThat(
            processedEvent.get("event_version")
        )
            .isEqualTo(1);

        assertThat(
            processedEvent.get("topic")
        )
            .isEqualTo(TOPIC);

        assertThat(
            processedEvent.get("partition_number")
        )
            .isEqualTo(0);

        assertThat(
            ((Number) processedEvent.get(
                "record_offset"
            )).longValue()
        )
            .isZero();
    }

    private void publishEvent()
        throws Exception {

        kafkaTemplate.send(
                TOPIC,
                TRANSACTION_ID.toString(),
                PAYLOAD
            )
            .get(
                20,
                TimeUnit.SECONDS
            );
    }

    private void assertStoredCounts() {
        Long processedEventCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM processed_kafka_events
                WHERE consumer_name = ?
                  AND event_id = ?
                """,
                Long.class,
                CONSUMER_NAME,
                EVENT_ID
            );

        Long auditCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transfer_completed_event_audits
                WHERE event_id = ?
                  AND transaction_id = ?
                """,
                Long.class,
                EVENT_ID,
                TRANSACTION_ID
            );

        assertThat(processedEventCount)
            .isEqualTo(1L);

        assertThat(auditCount)
            .isEqualTo(1L);
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
        NewTopic transferCompletedIntegrationTopic() {
            return TopicBuilder
                .name(TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
        }
    }
}
