package com.nursena.payflow.transaction.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.transaction.domain.exception.InvalidTransactionStateException;
import com.nursena.payflow.transaction.domain.exception.InvalidTransferAmountException;
import com.nursena.payflow.transaction.domain.exception.SelfTransferNotAllowedException;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.Money;
import org.junit.jupiter.api.Test;

class PaymentTransactionTest {

    private static final UUID SOURCE_WALLET_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final UUID TARGET_WALLET_ID =
        UUID.fromString(
            "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db"
        );

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-15T12:00:00Z");

    private static final Instant COMPLETED_AT =
        Instant.parse("2026-07-15T12:00:01Z");

    @Test
    void shouldStartTransferAsPending() {
        PaymentTransaction transaction =
            startTransfer();

        assertThat(transaction.id())
            .isNotNull();

        assertThat(transaction.sourceWalletId())
            .isEqualTo(SOURCE_WALLET_ID);

        assertThat(transaction.targetWalletId())
            .isEqualTo(TARGET_WALLET_ID);

        assertThat(transaction.amount())
            .isEqualTo(
                Money.of("125.50", Currency.TRY)
            );

        assertThat(transaction.type())
            .isEqualTo(TransactionType.TRANSFER);

        assertThat(transaction.status())
            .isEqualTo(TransactionStatus.PENDING);

        assertThat(transaction.createdAt())
            .isEqualTo(CREATED_AT);

        assertThat(transaction.completedAt())
            .isNull();
    }

    @Test
    void shouldCompletePendingTransfer() {
        PaymentTransaction transaction =
            startTransfer();

        transaction.complete(COMPLETED_AT);

        assertThat(transaction.status())
            .isEqualTo(TransactionStatus.COMPLETED);

        assertThat(transaction.completedAt())
            .isEqualTo(COMPLETED_AT);
    }

    @Test
    void shouldRejectCompletingTransferTwice() {
        PaymentTransaction transaction =
            startTransfer();

        transaction.complete(COMPLETED_AT);

        assertThatThrownBy(() ->
            transaction.complete(COMPLETED_AT)
        )
            .isInstanceOf(
                InvalidTransactionStateException.class
            );
    }

    @Test
    void shouldRejectTransferToSameWallet() {
        assertThatThrownBy(() ->
            PaymentTransaction.startTransfer(
                SOURCE_WALLET_ID,
                SOURCE_WALLET_ID,
                Money.of("10.00", Currency.TRY),
                new IdempotencyKey("request-1"),
                CREATED_AT
            )
        )
            .isInstanceOf(
                SelfTransferNotAllowedException.class
            )
            .hasMessage(
                "Source and target wallets "
                    + "must be different."
            );
    }

    @Test
    void shouldRejectNonPositiveTransferAmount() {
        assertThatThrownBy(() ->
            PaymentTransaction.startTransfer(
                SOURCE_WALLET_ID,
                TARGET_WALLET_ID,
                Money.of("0.00", Currency.TRY),
                new IdempotencyKey("request-1"),
                CREATED_AT
            )
        )
            .isInstanceOf(
                InvalidTransferAmountException.class
            );
    }

    private static PaymentTransaction startTransfer() {
        return PaymentTransaction.startTransfer(
            SOURCE_WALLET_ID,
            TARGET_WALLET_ID,
            Money.of("125.50", Currency.TRY),
            new IdempotencyKey("request-1"),
            CREATED_AT
        );
    }
}
