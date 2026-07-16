package com.nursena.payflow.transaction.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.transaction.application.model.TransactionDirection;
import com.nursena.payflow.transaction.application.model.TransactionHistoryPage;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.transaction.domain.model.TransactionType;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.transaction.application.model.TransactionHistoryFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.isNull;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryPersistenceAdapterTest {

    private static final UUID WALLET_ID =
        UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
        );

    private static final UUID COUNTERPARTY_WALLET_ID =
        UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
        );

    private static final UUID OUTGOING_TRANSACTION_ID =
        UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        );

    private static final UUID INCOMING_TRANSACTION_ID =
        UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        );

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-16T10:00:00Z");

    @Mock
    private SpringDataPaymentTransactionRepository
        repository;

    private TransactionHistoryPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter =
            new TransactionHistoryPersistenceAdapter(
                repository
            );
    }

    @Test
    void shouldMapOutgoingAndIncomingTransactions() {
        PaymentTransactionJpaEntity outgoing =
            transactionEntity(
                OUTGOING_TRANSACTION_ID,
                WALLET_ID,
                COUNTERPARTY_WALLET_ID
            );

        PaymentTransactionJpaEntity incoming =
            transactionEntity(
                INCOMING_TRANSACTION_ID,
                COUNTERPARTY_WALLET_ID,
                WALLET_ID
            );

        PageRequest repositoryPageRequest =
            PageRequest.of(1, 2);

        when(
            repository.findHistory(
                eq(WALLET_ID),
                eq(true),
                eq(true),
                eq(false),
                eq(TransactionStatus.PENDING),
                eq(false),
                eq(Instant.EPOCH),
                eq(false),
                eq(Instant.EPOCH),
                any(Pageable.class)
            )
        ).thenReturn(
            new PageImpl<>(
                List.of(outgoing, incoming),
                repositoryPageRequest,
                4
            )
        );

        TransactionHistoryPage result =
            adapter.findByWalletId(
                WALLET_ID,
                1,
                2,
                TransactionHistoryFilter.unfiltered()
            );

        assertThat(result.page())
            .isEqualTo(1);

        assertThat(result.size())
            .isEqualTo(2);

        assertThat(result.totalElements())
            .isEqualTo(4);

        assertThat(result.totalPages())
            .isEqualTo(2);

        assertThat(result.items())
            .hasSize(2);

        assertThat(result.items().get(0).transactionId())
            .isEqualTo(OUTGOING_TRANSACTION_ID);

        assertThat(result.items().get(0).direction())
            .isEqualTo(TransactionDirection.OUTGOING);

        assertThat(
            result.items()
                .get(0)
                .counterpartyWalletId()
        ).isEqualTo(COUNTERPARTY_WALLET_ID);

        assertThat(result.items().get(1).transactionId())
            .isEqualTo(INCOMING_TRANSACTION_ID);

        assertThat(result.items().get(1).direction())
            .isEqualTo(TransactionDirection.INCOMING);

        assertThat(
            result.items()
                .get(1)
                .counterpartyWalletId()
        ).isEqualTo(COUNTERPARTY_WALLET_ID);

        ArgumentCaptor<Pageable> pageableCaptor =
            ArgumentCaptor.forClass(Pageable.class);

        verify(repository)
            .findHistory(
                eq(WALLET_ID),
                eq(true),
                eq(true),
                eq(false),
                eq(TransactionStatus.PENDING),
                eq(false),
                eq(Instant.EPOCH),
                eq(false),
                eq(Instant.EPOCH),
                pageableCaptor.capture()
            );

        Pageable capturedPageable =
            pageableCaptor.getValue();

        assertThat(capturedPageable.getPageNumber())
            .isEqualTo(1);

        assertThat(capturedPageable.getPageSize())
            .isEqualTo(2);

        assertDescendingSort(
            capturedPageable,
            "createdAt"
        );

        assertDescendingSort(
            capturedPageable,
            "id"
        );
    }

    @Test
    void shouldRejectTransactionOutsideRequestedWallet() {
        UUID unrelatedSourceWalletId =
            UUID.fromString(
                "33333333-3333-3333-3333-333333333333"
            );

        UUID unrelatedTargetWalletId =
            UUID.fromString(
                "44444444-4444-4444-4444-444444444444"
            );

        PaymentTransactionJpaEntity unrelated =
            transactionEntity(
                OUTGOING_TRANSACTION_ID,
                unrelatedSourceWalletId,
                unrelatedTargetWalletId
            );

        when(
            repository.findHistory(
                eq(WALLET_ID),
                eq(true),
                eq(true),
                eq(false),
                eq(TransactionStatus.PENDING),
                eq(false),
                eq(Instant.EPOCH),
                eq(false),
                eq(Instant.EPOCH),
                any(Pageable.class)
            )
        ).thenReturn(
            new PageImpl<>(List.of(unrelated))
        );

        assertThatThrownBy(() ->
            adapter.findByWalletId(
                WALLET_ID,
                0,
                20,
                TransactionHistoryFilter.unfiltered()
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "Payment transaction must belong "
                    + "to exactly one side of the wallet."
            );
    }

    private static void assertDescendingSort(
        Pageable pageable,
        String property
    ) {
        Sort.Order order =
            pageable
                .getSort()
                .getOrderFor(property);

        assertThat(order)
            .isNotNull();

        assertThat(order.getDirection())
            .isEqualTo(Sort.Direction.DESC);
    }

    private static PaymentTransactionJpaEntity
    transactionEntity(
        UUID transactionId,
        UUID sourceWalletId,
        UUID targetWalletId
    ) {
        return new PaymentTransactionJpaEntity(
            transactionId,
            sourceWalletId,
            targetWalletId,
            TransactionType.TRANSFER,
            TransactionStatus.COMPLETED,
            new BigDecimal("125.50"),
            Currency.TRY,
            transactionId.toString(),
            null,
            CREATED_AT,
            CREATED_AT.plusSeconds(1)
        );
    }
}
