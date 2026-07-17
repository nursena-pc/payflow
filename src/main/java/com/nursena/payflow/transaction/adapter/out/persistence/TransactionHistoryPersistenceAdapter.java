package com.nursena.payflow.transaction.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.transaction.application.model.TransactionDirection;
import com.nursena.payflow.transaction.application.model.TransactionHistoryFilter;
import com.nursena.payflow.transaction.application.model.TransactionHistoryItem;
import com.nursena.payflow.transaction.application.model.TransactionHistoryPage;
import com.nursena.payflow.transaction.application.port.out.TransactionHistoryQueryPort;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
class TransactionHistoryPersistenceAdapter
    implements TransactionHistoryQueryPort {

    private static final Sort HISTORY_SORT =
        Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
        );

    private final SpringDataPaymentTransactionRepository
        repository;

    TransactionHistoryPersistenceAdapter(
        SpringDataPaymentTransactionRepository repository
    ) {
        this.repository = Objects.requireNonNull(
            repository,
            "repository must not be null"
        );
    }

    @Override
    public TransactionHistoryPage findByWalletId(
        UUID walletId,
        int page,
        int size,
        TransactionHistoryFilter filter
    ) {
        Objects.requireNonNull(
            walletId,
            "walletId must not be null"
        );

        Objects.requireNonNull(
            filter,
            "filter must not be null"
        );

        PageRequest pageRequest =
            PageRequest.of(
                page,
                size,
                HISTORY_SORT
            );

        UUID sourceWalletId =
            filter.direction()
                == TransactionDirection.INCOMING
                ? null
                : walletId;

        UUID targetWalletId =
            filter.direction()
                == TransactionDirection.OUTGOING
                ? null
                : walletId;

        boolean includeOutgoing =
            filter.direction()
                != TransactionDirection.INCOMING;

        boolean includeIncoming =
            filter.direction()
                != TransactionDirection.OUTGOING;

        boolean filterByStatus =
            filter.status() != null;

        boolean filterByFrom =
            filter.from() != null;

        boolean filterByTo =
            filter.to() != null;

        TransactionStatus statusParameter =
            filterByStatus
                ? filter.status()
                : TransactionStatus.PENDING;

        Instant fromParameter =
            filterByFrom
                ? filter.from()
                : Instant.EPOCH;

        Instant toParameter =
            filterByTo
                ? filter.to()
                : Instant.EPOCH;

        Page<PaymentTransactionJpaEntity>
            transactionPage =
            repository.findHistory(
                walletId,
                includeOutgoing,
                includeIncoming,
                filterByStatus,
                statusParameter,
                filterByFrom,
                fromParameter,
                filterByTo,
                toParameter,
                pageRequest
            );

        List<TransactionHistoryItem> items =
            transactionPage
                .getContent()
                .stream()
                .map(entity ->
                    toHistoryItem(
                        walletId,
                        entity
                    )
                )
                .toList();

        return new TransactionHistoryPage(
            items,
            transactionPage.getNumber(),
            transactionPage.getSize(),
            transactionPage.getTotalElements(),
            transactionPage.getTotalPages()
        );
    }

    private static TransactionHistoryItem
    toHistoryItem(
        UUID walletId,
        PaymentTransactionJpaEntity entity
    ) {
        boolean outgoing = walletId.equals(
            entity.getSourceWalletId()
        );

        boolean incoming = walletId.equals(
            entity.getTargetWalletId()
        );

        if (outgoing == incoming) {
            throw new IllegalStateException(
                "Payment transaction must belong "
                    + "to exactly one side of the wallet."
            );
        }

        TransactionDirection direction =
            outgoing
                ? TransactionDirection.OUTGOING
                : TransactionDirection.INCOMING;

        UUID counterpartyWalletId =
            outgoing
                ? entity.getTargetWalletId()
                : entity.getSourceWalletId();

        if (counterpartyWalletId == null) {
            throw new IllegalStateException(
                "Transfer counterparty wallet "
                    + "must not be null."
            );
        }

        return new TransactionHistoryItem(
            entity.getId(),
            entity.getTransactionType(),
            direction,
            counterpartyWalletId,
            entity.getAmount(),
            entity.getCurrency(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getCompletedAt()
        );
    }
}
