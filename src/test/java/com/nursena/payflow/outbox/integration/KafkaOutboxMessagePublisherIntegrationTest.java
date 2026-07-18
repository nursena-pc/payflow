package com.nursena.payflow.outbox.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.nursena.payflow.outbox.application.port.out.OutboxMessagePublisherPort;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import com.nursena.payflow.outbox.domain.model.OutboxStatus;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
@Testcontainers
class KafkaOutboxMessagePublisherIntegrationTest {

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000101"
        );

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000000101"
        );

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-18T18:00:00Z"
        );

    private static final String TOPIC =
        "wallet.transfer.completed";

    private static final String PARTITION_KEY =
        TRANSACTION_ID.toString();

    private static final String CONSUMER_GROUP =
        "outbox-kafka-publisher-integration-test";

    private static final String PAYLOAD = """
        {
          "eventId": "50000000-0000-0000-0000-000000000101",
          "eventType": "wallet.transfer.completed",
          "eventVersion": 1,
          "occurredAt": "2026-07-18T18:00:00Z",
          "transactionId": "60000000-0000-0000-0000-000000000101",
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
    private OutboxMessagePublisherPort publisherPort;

    @BeforeAll
    static void createTopic() throws Exception {
        Map<String, Object> adminProperties =
            Map.of(
                AdminClientConfig
                    .BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
            );

        try (
            Admin admin =
                Admin.create(adminProperties)
        ) {
            NewTopic topic =
                new NewTopic(
                    TOPIC,
                    1,
                    (short) 1
                );

            admin.createTopics(
                    List.of(topic)
                )
                .all()
                .get(
                    30,
                    TimeUnit.SECONDS
                );
        }
    }

    @Test
    void shouldPublishRawOutboxPayloadWithExpectedTopicAndKey() {
        try (
            Consumer<String, String> consumer =
                createConsumer()
        ) {
            consumer.subscribe(
                List.of(TOPIC)
            );

            publisherPort.publish(
                processingEvent()
            );

            ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(
                    consumer,
                    TOPIC,
                    Duration.ofSeconds(20)
                );

            assertThat(record.topic())
                .isEqualTo(TOPIC);

            assertThat(record.partition())
                .isZero();

            assertThat(record.key())
                .isEqualTo(PARTITION_KEY);

            assertThat(record.value())
                .isEqualTo(PAYLOAD);
        }
    }

    private static Consumer<String, String>
    createConsumer() {
        Map<String, Object> properties =
            new HashMap<>();

        properties.put(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KAFKA.getBootstrapServers()
        );

        properties.put(
            ConsumerConfig.GROUP_ID_CONFIG,
            CONSUMER_GROUP
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

    private static OutboxEvent processingEvent() {
        return OutboxEvent.rehydrate(
            EVENT_ID,
            "PAYMENT_TRANSACTION",
            TRANSACTION_ID,
            TOPIC,
            1,
            TOPIC,
            PARTITION_KEY,
            TOPIC + ":1:" + TRANSACTION_ID,
            PAYLOAD,
            OutboxStatus.PROCESSING,
            1,
            CREATED_AT,
            CREATED_AT,
            CREATED_AT.plusSeconds(30),
            "publisher-integration-test",
            CREATED_AT.minusSeconds(60),
            null,
            null
        );
    }
}
