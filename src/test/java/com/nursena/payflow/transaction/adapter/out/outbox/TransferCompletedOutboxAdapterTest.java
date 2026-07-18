package com.nursena.payflow.transaction.adapter.out.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nursena.payflow.outbox.application.port.out.OutboxEventRepositoryPort;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import com.nursena.payflow.outbox.domain.model.OutboxStatus;
import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferCompletedOutboxAdapterTest {

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000001"
        );

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000000001"
        );

    private static final UUID SOURCE_WALLET_ID =
        UUID.fromString(
            "70000000-0000-0000-0000-000000000001"
        );

    private static final UUID TARGET_WALLET_ID =
        UUID.fromString(
            "70000000-0000-0000-0000-000000000002"
        );

    private static final Instant OCCURRED_AT =
        Instant.parse(
            "2026-07-17T19:00:00Z"
        );

    @Mock
    private OutboxEventRepositoryPort
        outboxEventRepository;

    private ObjectMapper objectMapper;

    private TransferCompletedOutboxAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper =
            JsonMapper.builder()
                .addModule(
                    new JavaTimeModule()
                )
                .disable(
                    SerializationFeature
                        .WRITE_DATES_AS_TIMESTAMPS
                )
                .build();

        adapter =
            new TransferCompletedOutboxAdapter(
                outboxEventRepository,
                objectMapper
            );
    }

    @Test
    void shouldConvertAndRecordTransferEvent()
        throws Exception {

        when(outboxEventRepository.save(
            any(OutboxEvent.class)
        )).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        adapter.record(event());

        ArgumentCaptor<OutboxEvent> captor =
            ArgumentCaptor.forClass(
                OutboxEvent.class
            );

        verify(outboxEventRepository)
            .save(captor.capture());

        OutboxEvent outboxEvent =
            captor.getValue();

        assertThat(outboxEvent.id())
            .isEqualTo(EVENT_ID);

        assertThat(outboxEvent.aggregateType())
            .isEqualTo(
                "PAYMENT_TRANSACTION"
            );

        assertThat(outboxEvent.aggregateId())
            .isEqualTo(TRANSACTION_ID);

        assertThat(outboxEvent.eventType())
            .isEqualTo(
                TransferCompletedEvent.TYPE
            );

        assertThat(outboxEvent.eventVersion())
            .isEqualTo(
                TransferCompletedEvent.VERSION
            );

        assertThat(outboxEvent.topic())
            .isEqualTo(
                "wallet.transfer.completed"
            );

        assertThat(outboxEvent.partitionKey())
            .isEqualTo(
                TRANSACTION_ID.toString()
            );

        assertThat(
            outboxEvent.deduplicationKey()
        ).isEqualTo(
            "wallet.transfer.completed:1:"
                + TRANSACTION_ID
        );

        assertThat(outboxEvent.status())
            .isEqualTo(OutboxStatus.PENDING);

        assertThat(outboxEvent.attemptCount())
            .isZero();

        assertThat(outboxEvent.availableAt())
            .isEqualTo(OCCURRED_AT);

        assertThat(outboxEvent.createdAt())
            .isEqualTo(OCCURRED_AT);

        JsonNode payload =
            objectMapper.readTree(
                outboxEvent.payload()
            );

        assertThat(payload.size())
            .isEqualTo(9);

        assertThat(
            payload.get("eventId").asText()
        ).isEqualTo(
            EVENT_ID.toString()
        );

        assertThat(
            payload.get("eventType").asText()
        ).isEqualTo(
            "wallet.transfer.completed"
        );

        assertThat(
            payload.get("eventVersion").asInt()
        ).isEqualTo(1);

        assertThat(
            payload.get("occurredAt").asText()
        ).isEqualTo(
            "2026-07-17T19:00:00Z"
        );

        assertThat(
            payload.get("transactionId").asText()
        ).isEqualTo(
            TRANSACTION_ID.toString()
        );

        assertThat(
            payload.get("sourceWalletId").asText()
        ).isEqualTo(
            SOURCE_WALLET_ID.toString()
        );

        assertThat(
            payload.get("targetWalletId").asText()
        ).isEqualTo(
            TARGET_WALLET_ID.toString()
        );

        assertThat(payload.get("amount").asText())
            .isEqualTo("125.50");

        assertThat(
            payload.get("currency").asText()
        ).isEqualTo("TRY");

        assertThat(payload.has("idempotencyKey"))
            .isFalse();
    }

    @Test
    void shouldFailBeforePersistenceWhenSerializationFails()
        throws Exception {

        ObjectMapper failingObjectMapper =
            mock(ObjectMapper.class);

        JsonProcessingException failure =
            new JsonProcessingException(
                "Simulated serialization failure."
            ) {
            };

        when(failingObjectMapper.writeValueAsString(
            any()
        )).thenThrow(failure);

        TransferCompletedOutboxAdapter
            failingAdapter =
            new TransferCompletedOutboxAdapter(
                outboxEventRepository,
                failingObjectMapper
            );

        assertThatThrownBy(() ->
            failingAdapter.record(event())
        )
            .isInstanceOf(
                TransferCompletedEventSerializationException.class
            )
            .hasMessage(
                "Transfer completed event "
                    + "could not be serialized."
            )
            .hasCause(failure);

        verify(outboxEventRepository, never())
            .save(any(OutboxEvent.class));
    }

    private static TransferCompletedEvent event() {
        return new TransferCompletedEvent(
            EVENT_ID,
            TransferCompletedEvent.TYPE,
            TransferCompletedEvent.VERSION,
            OCCURRED_AT,
            TRANSACTION_ID,
            SOURCE_WALLET_ID,
            TARGET_WALLET_ID,
            "125.50",
            "TRY"
        );
    }
}
