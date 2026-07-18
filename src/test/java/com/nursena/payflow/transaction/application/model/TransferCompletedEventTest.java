package com.nursena.payflow.transaction.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nursena.payflow.transaction.domain.model.IdempotencyKey;
import com.nursena.payflow.transaction.domain.model.PaymentTransaction;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.transaction.domain.model.TransactionType;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.Money;
import org.junit.jupiter.api.Test;

class TransferCompletedEventTest {

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

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-17T18:59:59Z"
        );

    private static final Instant COMPLETED_AT =
        Instant.parse(
            "2026-07-17T19:00:00Z"
        );

    private static final ObjectMapper OBJECT_MAPPER =
        JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(
                SerializationFeature
                    .WRITE_DATES_AS_TIMESTAMPS
            )
            .build();

    @Test
    void shouldCreateEventFromCompletedTransfer() {
        TransferCompletedEvent event =
            TransferCompletedEvent.from(
                completedTransaction()
            );

        assertThat(event.eventId())
            .isNotNull();

        assertThat(event.eventType())
            .isEqualTo(
                "wallet.transfer.completed"
            );

        assertThat(event.eventVersion())
            .isEqualTo(1);

        assertThat(event.occurredAt())
            .isEqualTo(COMPLETED_AT);

        assertThat(event.transactionId())
            .isEqualTo(TRANSACTION_ID);

        assertThat(event.sourceWalletId())
            .isEqualTo(SOURCE_WALLET_ID);

        assertThat(event.targetWalletId())
            .isEqualTo(TARGET_WALLET_ID);

        assertThat(event.amount())
            .isEqualTo("125.50");

        assertThat(event.currency())
            .isEqualTo("TRY");
    }

    @Test
    void shouldSerializeStablePublicContract()
        throws Exception {

        TransferCompletedEvent event =
            TransferCompletedEvent.from(
                completedTransaction()
            );

        JsonNode payload =
            OBJECT_MAPPER.readTree(
                OBJECT_MAPPER.writeValueAsString(
                    event
                )
            );

        assertThat(payload.size())
            .isEqualTo(9);

        assertThat(payload.get("eventId").isTextual())
            .isTrue();

        assertThat(
            UUID.fromString(
                payload.get("eventId").asText()
            )
        ).isEqualTo(event.eventId());

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

        assertThat(payload.get("amount").isTextual())
            .isTrue();

        assertThat(payload.get("amount").asText())
            .isEqualTo("125.50");

        assertThat(payload.get("currency").isTextual())
            .isTrue();

        assertThat(payload.get("currency").asText())
            .isEqualTo("TRY");

        assertThat(payload.has("idempotencyKey"))
            .isFalse();

        assertThat(payload.has("password"))
            .isFalse();

        assertThat(payload.has("ownerId"))
            .isFalse();
    }

    @Test
    void shouldRejectTransactionThatIsNotCompleted() {
        PaymentTransaction pending =
            PaymentTransaction.rehydrate(
                TRANSACTION_ID,
                SOURCE_WALLET_ID,
                TARGET_WALLET_ID,
                Money.of(
                    "125.50",
                    Currency.TRY
                ),
                new IdempotencyKey(
                    "request-1"
                ),
                TransactionType.TRANSFER,
                TransactionStatus.PENDING,
                CREATED_AT,
                null
            );

        assertThatThrownBy(() ->
            TransferCompletedEvent.from(pending)
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "transaction must be completed."
            );
    }

    @Test
    void shouldRejectCompletedTransactionWithoutTimestamp() {
        PaymentTransaction invalid =
            PaymentTransaction.rehydrate(
                TRANSACTION_ID,
                SOURCE_WALLET_ID,
                TARGET_WALLET_ID,
                Money.of(
                    "125.50",
                    Currency.TRY
                ),
                new IdempotencyKey(
                    "request-1"
                ),
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                CREATED_AT,
                null
            );

        assertThatThrownBy(() ->
            TransferCompletedEvent.from(invalid)
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "completed transaction must have completedAt."
            );
    }

    private static PaymentTransaction
    completedTransaction() {

        return PaymentTransaction.rehydrate(
            TRANSACTION_ID,
            SOURCE_WALLET_ID,
            TARGET_WALLET_ID,
            Money.of(
                "125.50",
                Currency.TRY
            ),
            new IdempotencyKey(
                "request-1"
            ),
            TransactionType.TRANSFER,
            TransactionStatus.COMPLETED,
            CREATED_AT,
            COMPLETED_AT
        );
    }
}
