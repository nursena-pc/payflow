package com.nursena.payflow.outbox.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import com.nursena.payflow.outbox.domain.model.OutboxStatus;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxMessagePublisherAdapterTest {

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000001"
        );

    private static final UUID AGGREGATE_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000000001"
        );

    private static final String TOPIC =
        "wallet.transfer.completed";

    private static final String PARTITION_KEY =
        AGGREGATE_ID.toString();

    private static final String PAYLOAD = """
        {
          "eventId": "50000000-0000-0000-0000-000000000001",
          "eventType": "wallet.transfer.completed",
          "eventVersion": 1
        }
        """;

    private static final Instant NOW =
        Instant.parse(
            "2026-07-18T18:00:00Z"
        );

    @Mock
    private KafkaOperations<String, String>
        kafkaOperations;

    private KafkaOutboxMessagePublisherAdapter
        adapter;

    @BeforeEach
    void setUp() {
        adapter =
            new KafkaOutboxMessagePublisherAdapter(
                kafkaOperations,
                Duration.ofSeconds(5)
            );
    }

    @Test
    void shouldPublishAndWaitForBrokerResult() {
        SendResult<String, String> sendResult =
            mock(SendResult.class);

        when(kafkaOperations.send(
            TOPIC,
            PARTITION_KEY,
            PAYLOAD
        )).thenReturn(
            CompletableFuture.completedFuture(
                sendResult
            )
        );

        adapter.publish(processingEvent());

        verify(kafkaOperations)
            .send(
                TOPIC,
                PARTITION_KEY,
                PAYLOAD
            );
    }

    @Test
    void shouldTranslateAsynchronousKafkaFailure() {
        CompletableFuture<
            SendResult<String, String>
            > failedFuture =
            CompletableFuture.failedFuture(
                new KafkaException(
                    "Broker is unavailable."
                )
            );

        when(kafkaOperations.send(
            TOPIC,
            PARTITION_KEY,
            PAYLOAD
        )).thenReturn(failedFuture);

        assertThatThrownBy(() ->
            adapter.publish(processingEvent())
        )
            .isInstanceOf(
                OutboxMessagePublishingException.class
            )
            .hasMessageContaining(
                EVENT_ID.toString()
            )
            .hasRootCauseMessage(
                "Broker is unavailable."
            );
    }

    @Test
    void shouldTranslateBrokerTimeout() throws Exception {
        @SuppressWarnings("unchecked")
        CompletableFuture<
            SendResult<String, String>
            > sendFuture =
            mock(CompletableFuture.class);

        when(kafkaOperations.send(
            TOPIC,
            PARTITION_KEY,
            PAYLOAD
        )).thenReturn(sendFuture);

        when(sendFuture.get(
            5_000,
            TimeUnit.MILLISECONDS
        )).thenThrow(
            new TimeoutException(
                "Kafka acknowledgement timed out."
            )
        );

        assertThatThrownBy(() ->
            adapter.publish(processingEvent())
        )
            .isInstanceOf(
                OutboxMessagePublishingException.class
            )
            .hasMessageContaining(
                "acknowledgement timed out"
            );
    }

    @Test
    void shouldRestoreInterruptStatus() throws Exception {
        @SuppressWarnings("unchecked")
        CompletableFuture<
            SendResult<String, String>
            > sendFuture =
            mock(CompletableFuture.class);

        when(kafkaOperations.send(
            TOPIC,
            PARTITION_KEY,
            PAYLOAD
        )).thenReturn(sendFuture);

        when(sendFuture.get(
            5_000,
            TimeUnit.MILLISECONDS
        )).thenThrow(
            new InterruptedException(
                "Publisher thread interrupted."
            )
        );

        try {
            assertThatThrownBy(() ->
                adapter.publish(processingEvent())
            )
                .isInstanceOf(
                    OutboxMessagePublishingException.class
                );

            assertThat(
                Thread.currentThread()
                    .isInterrupted()
            ).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private static OutboxEvent processingEvent() {
        return OutboxEvent.rehydrate(
            EVENT_ID,
            "PAYMENT_TRANSACTION",
            AGGREGATE_ID,
            TOPIC,
            1,
            TOPIC,
            PARTITION_KEY,
            TOPIC + ":1:" + AGGREGATE_ID,
            PAYLOAD,
            OutboxStatus.PROCESSING,
            1,
            NOW,
            NOW,
            NOW.plusSeconds(30),
            "publisher-1",
            NOW.minusSeconds(60),
            null,
            null
        );
    }
}
