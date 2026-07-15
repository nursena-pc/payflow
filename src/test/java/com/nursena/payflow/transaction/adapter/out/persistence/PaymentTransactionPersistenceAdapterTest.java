package com.nursena.payflow.transaction.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.transaction.domain.exception.PaymentTransactionNotFoundException;
import com.nursena.payflow.transaction.domain.model.IdempotencyKey;
import com.nursena.payflow.transaction.domain.model.PaymentTransaction;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.transaction.domain.model.TransactionType;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionPersistenceAdapterTest {

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "b4077781-34f4-466f-8e61-b79ca906bc98"
        );

    private static final UUID SOURCE_WALLET_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final UUID TARGET_WALLET_ID =
        UUID.fromString(
            "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db"
        );

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-15T16:00:00Z");

    private static final Instant COMPLETED_AT =
        Instant.parse("2026-07-15T16:00:01Z");

    @Mock
    private SpringDataPaymentTransactionRepository
        repository;

    private PaymentTransactionPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter =
            new PaymentTransactionPersistenceAdapter(
                repository
            );
    }

    @Test
    void shouldSaveAndRestoreTransaction() {
        PaymentTransaction transaction =
            pendingTransaction();

        when(repository.saveAndFlush(
            any(PaymentTransactionJpaEntity.class)
        )).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        PaymentTransaction saved =
            adapter.save(transaction);

        assertThat(saved.id())
            .isEqualTo(TRANSACTION_ID);

        assertThat(saved.sourceWalletId())
            .isEqualTo(SOURCE_WALLET_ID);

        assertThat(saved.targetWalletId())
            .isEqualTo(TARGET_WALLET_ID);

        assertThat(saved.amount())
            .isEqualTo(
                Money.of("125.50", Currency.TRY)
            );

        assertThat(saved.idempotencyKey())
            .isEqualTo(
                new IdempotencyKey("request-1")
            );

        assertThat(saved.type())
            .isEqualTo(TransactionType.TRANSFER);

        assertThat(saved.status())
            .isEqualTo(TransactionStatus.PENDING);

        verify(repository)
            .saveAndFlush(
                any(PaymentTransactionJpaEntity.class)
            );
    }

    @Test
    void shouldFindTransactionBySourceAndIdempotencyKey() {
        PaymentTransactionJpaEntity entity =
            pendingEntity();

        when(
            repository
                .findBySourceWalletIdAndIdempotencyKey(
                    SOURCE_WALLET_ID,
                    "request-1"
                )
        ).thenReturn(Optional.of(entity));

        Optional<PaymentTransaction> result =
            adapter
                .findBySourceWalletIdAndIdempotencyKey(
                    SOURCE_WALLET_ID,
                    new IdempotencyKey("request-1")
                );

        assertThat(result).isPresent();

        assertThat(result.orElseThrow().id())
            .isEqualTo(TRANSACTION_ID);

        verify(repository)
            .findBySourceWalletIdAndIdempotencyKey(
                SOURCE_WALLET_ID,
                "request-1"
            );
    }

    @Test
    void shouldUpdateManagedTransactionState() {
        PaymentTransaction transaction =
            pendingTransaction();

        transaction.complete(COMPLETED_AT);

        PaymentTransactionJpaEntity entity =
            pendingEntity();

        when(repository.findById(TRANSACTION_ID))
            .thenReturn(Optional.of(entity));

        PaymentTransaction updated =
            adapter.update(transaction);

        assertThat(updated.status())
            .isEqualTo(TransactionStatus.COMPLETED);

        assertThat(updated.completedAt())
            .isEqualTo(COMPLETED_AT);

        verify(repository)
            .findById(TRANSACTION_ID);

        verify(repository)
            .flush();
    }

    @Test
    void shouldRejectUpdatingMissingTransaction() {
        PaymentTransaction transaction =
            pendingTransaction();

        transaction.complete(COMPLETED_AT);

        when(repository.findById(TRANSACTION_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            adapter.update(transaction)
        )
            .isInstanceOf(
                PaymentTransactionNotFoundException.class
            )
            .hasMessage(
                "Payment transaction could not be found."
            );
    }

    private static PaymentTransaction pendingTransaction() {
        return PaymentTransaction.rehydrate(
            TRANSACTION_ID,
            SOURCE_WALLET_ID,
            TARGET_WALLET_ID,
            Money.of("125.50", Currency.TRY),
            new IdempotencyKey("request-1"),
            TransactionType.TRANSFER,
            TransactionStatus.PENDING,
            CREATED_AT,
            null
        );
    }

    private static PaymentTransactionJpaEntity pendingEntity() {
        return new PaymentTransactionJpaEntity(
            TRANSACTION_ID,
            SOURCE_WALLET_ID,
            TARGET_WALLET_ID,
            TransactionType.TRANSFER,
            TransactionStatus.PENDING,
            Money.of(
                "125.50",
                Currency.TRY
            ).amount(),
            Currency.TRY,
            "request-1",
            null,
            CREATED_AT,
            null
        );
    }
}
