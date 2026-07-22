package com.nursena.payflow.eventprocessing.adapter.out.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import com.nursena.payflow.eventprocessing.adapter.kafka.KafkaDeadLetterReplayHeaders;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaDeadLetterReplayPublisherAdapterTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001701"
        );

    private static final UUID REPLAY_ORIGIN_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001700"
        );

    private static final String ORIGINAL_TOPIC =
        "wallet.transfer.completed";

    private static final String DEAD_LETTER_TOPIC =
        "wallet.transfer.completed.dlt";

    private static final String RECORD_KEY =
        "transaction-id";

    private static final String PAYLOAD = """
        {
          "eventId":
            "80000000-0000-0000-0000-000000001702"
        }
        """;

    private static final Duration SEND_TIMEOUT =
        Duration.ofSeconds(5);

    private static final Instant RECEIVED_AT =
        Instant.parse(
            "2026-07-21T20:00:00Z"
        );

    private static final Instant CLAIMED_AT =
        Instant.parse(
            "2026-07-21T20:05:00Z"
        );

    @Mock
    private KafkaOperations<String, String>
        kafkaOperations;

    private KafkaDeadLetterReplayPublisherAdapter
        adapter;

    @BeforeEach
    void setUp() {
        adapter =
            new KafkaDeadLetterReplayPublisherAdapter(
                kafkaOperations,
                SEND_TIMEOUT
            );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPublishOriginalMessageWithReplayLineage()
        throws Exception {

        CompletableFuture<
            SendResult<String, String>
            > sendFuture =
            mock(CompletableFuture.class);

        SendResult<String, String> sendResult =
            mock(SendResult.class);

        when(kafkaOperations.send(
            any(ProducerRecord.class)
        )).thenReturn(sendFuture);

        when(sendFuture.get(
            5_000,
            MILLISECONDS
        )).thenReturn(sendResult);

        KafkaDeadLetterRecord record =
            replayingRecord(
                RECORD_KEY,
                PAYLOAD,
                ORIGINAL_TOPIC,
                DEAD_LETTER_TOPIC,
                3,
                2
            );

        adapter.publish(record);

        ArgumentCaptor<
            ProducerRecord<String, String>
            > captor =
            ArgumentCaptor.forClass(
                ProducerRecord.class
            );

        verify(kafkaOperations)
            .send(captor.capture());

        verify(sendFuture)
            .get(
                5_000,
                MILLISECONDS
            );

        ProducerRecord<String, String> published =
            captor.getValue();

        assertThat(published.topic())
            .isEqualTo(ORIGINAL_TOPIC);

        assertThat(published.partition())
            .isNull();

        assertThat(published.key())
            .isEqualTo(RECORD_KEY);

        assertThat(published.value())
            .isEqualTo(PAYLOAD);

        Header[] headers =
            published.headers()
                .toArray();

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
         * replayAttemptBase 2 + replayCount 3
         * gives a chain-wide attempt value of 5.
         */
        assertThat(
            headerValue(
                published,
                KafkaDeadLetterReplayHeaders
                    .REPLAY_ATTEMPT
            )
        )
            .isEqualTo("5");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreserveNullableOriginalKey() {
        SendResult<String, String> sendResult =
            mock(SendResult.class);

        when(kafkaOperations.send(
            any(ProducerRecord.class)
        )).thenReturn(
            CompletableFuture.completedFuture(
                sendResult
            )
        );

        adapter.publish(
            replayingRecord(
                null,
                PAYLOAD,
                ORIGINAL_TOPIC,
                DEAD_LETTER_TOPIC,
                1,
                0
            )
        );

        ArgumentCaptor<
            ProducerRecord<String, String>
            > captor =
            ArgumentCaptor.forClass(
                ProducerRecord.class
            );

        verify(kafkaOperations)
            .send(captor.capture());

        assertThat(
            captor.getValue().key()
        )
            .isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldTranslateAsynchronousBrokerFailure() {
        CompletableFuture<
            SendResult<String, String>
            > failedFuture =
            CompletableFuture.failedFuture(
                new KafkaException(
                    "Broker is unavailable."
                )
            );

        when(kafkaOperations.send(
            any(ProducerRecord.class)
        )).thenReturn(failedFuture);

        assertThatThrownBy(() ->
            adapter.publish(
                replayingRecord()
            )
        )
            .isInstanceOf(
                KafkaDeadLetterReplayPublishingException
                    .class
            )
            .hasMessageContaining(
                RECORD_ID.toString()
            )
            .hasMessageContaining(
                ORIGINAL_TOPIC
            )
            .hasMessageContaining(
                "rejected or failed"
            )
            .hasRootCauseMessage(
                "Broker is unavailable."
            );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldTranslateBrokerAcknowledgementTimeout()
        throws Exception {

        CompletableFuture<
            SendResult<String, String>
            > sendFuture =
            mock(CompletableFuture.class);

        when(kafkaOperations.send(
            any(ProducerRecord.class)
        )).thenReturn(sendFuture);

        when(sendFuture.get(
            5_000,
            MILLISECONDS
        )).thenThrow(
            new TimeoutException(
                "Kafka acknowledgement timed out."
            )
        );

        assertThatThrownBy(() ->
            adapter.publish(
                replayingRecord()
            )
        )
            .isInstanceOf(
                KafkaDeadLetterReplayPublishingException
                    .class
            )
            .hasMessageContaining(
                "acknowledgement timed out"
            )
            .hasRootCauseMessage(
                "Kafka acknowledgement timed out."
            );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRestoreInterruptStatus()
        throws Exception {

        CompletableFuture<
            SendResult<String, String>
            > sendFuture =
            mock(CompletableFuture.class);

        when(kafkaOperations.send(
            any(ProducerRecord.class)
        )).thenReturn(sendFuture);

        when(sendFuture.get(
            5_000,
            MILLISECONDS
        )).thenThrow(
            new InterruptedException(
                "Publisher thread interrupted."
            )
        );

        try {
            assertThatThrownBy(() ->
                adapter.publish(
                    replayingRecord()
                )
            )
                .isInstanceOf(
                    KafkaDeadLetterReplayPublishingException
                        .class
                )
                .hasMessageContaining(
                    "Kafka send was interrupted"
                );

            assertThat(
                Thread.currentThread()
                    .isInterrupted()
            )
                .isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldTranslateSynchronousSendFailure() {
        when(kafkaOperations.send(
            any(ProducerRecord.class)
        )).thenThrow(
            new KafkaException(
                "Producer is closed."
            )
        );

        assertThatThrownBy(() ->
            adapter.publish(
                replayingRecord()
            )
        )
            .isInstanceOf(
                KafkaDeadLetterReplayPublishingException
                    .class
            )
            .hasMessageContaining(
                "could not be started"
            )
            .hasRootCauseMessage(
                "Producer is closed."
            );
    }

    @Test
    void shouldRejectRecordThatIsNotReplaying() {
        assertThatThrownBy(() ->
            adapter.publish(
                receivedRecord()
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "Only REPLAYING dead-letter "
                    + "records may be published."
            );
    }

    @Test
    void shouldRejectBlankReplayPayload() {
        assertThatThrownBy(() ->
            adapter.publish(
                replayingRecord(
                    RECORD_KEY,
                    " ",
                    ORIGINAL_TOPIC,
                    DEAD_LETTER_TOPIC,
                    1,
                    0
                )
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "Replay payload must not be blank."
            );
    }

    @Test
    void shouldRejectDeadLetterTopicAsReplaySource() {
        assertThatThrownBy(() ->
            adapter.publish(
                replayingRecord(
                    RECORD_KEY,
                    PAYLOAD,
                    DEAD_LETTER_TOPIC,
                    DEAD_LETTER_TOPIC,
                    1,
                    0
                )
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "Replay source topic must differ "
                    + "from the dead-letter topic."
            );
    }

    @Test
    void shouldValidateSendTimeout() {
        assertThatThrownBy(() ->
            new KafkaDeadLetterReplayPublisherAdapter(
                kafkaOperations,
                Duration.ZERO
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "sendTimeout must be positive."
            );

        assertThatThrownBy(() ->
            new KafkaDeadLetterReplayPublisherAdapter(
                kafkaOperations,
                Duration.ofNanos(999_999)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "sendTimeout must be at least "
                    + "one millisecond."
            );
    }

    private static String headerValue(
        ProducerRecord<String, String> record,
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

    private static KafkaDeadLetterRecord
    replayingRecord() {
        return replayingRecord(
            RECORD_KEY,
            PAYLOAD,
            ORIGINAL_TOPIC,
            DEAD_LETTER_TOPIC,
            3,
            2
        );
    }

    private static KafkaDeadLetterRecord
    replayingRecord(
        String recordKey,
        String payload,
        String originalTopic,
        String deadLetterTopic,
        int replayCount,
        int replayAttemptBase
    ) {
        UUID originId =
            replayAttemptBase == 0
                ? RECORD_ID
                : REPLAY_ORIGIN_ID;

        return new KafkaDeadLetterRecord(
            RECORD_ID,
            deadLetterTopic,
            0,
            25L,
            originalTopic,
            0,
            10L,
            "payflow-transfer-completed-audit-v1",
            recordKey,
            payload,
            "java.lang.IllegalStateException",
            "Temporary processing failure.",
            KafkaDeadLetterRecordStatus.REPLAYING,
            replayCount,
            RECEIVED_AT,
            CLAIMED_AT,
            "replay-worker-1",
            CLAIMED_AT.plusSeconds(30),
            null,
            originId,
            replayAttemptBase
        );
    }

    private static KafkaDeadLetterRecord
    receivedRecord() {
        return new KafkaDeadLetterRecord(
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
            KafkaDeadLetterRecordStatus.RECEIVED,
            0,
            RECEIVED_AT,
            null,
            null,
            null,
            null,
            RECORD_ID,
            0
        );
    }
}
