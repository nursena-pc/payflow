package com.nursena.payflow.ledger.adapter.out.persistence;

import java.util.List;

import com.nursena.payflow.ledger.application.port.out.LedgerRepositoryPort;
import com.nursena.payflow.ledger.domain.exception.InvalidLedgerEntryPairException;
import com.nursena.payflow.ledger.domain.model.DoubleEntryLedger;
import com.nursena.payflow.ledger.domain.model.LedgerEntry;
import com.nursena.payflow.ledger.domain.model.LedgerEntryType;
import com.nursena.payflow.wallet.domain.model.Money;
import org.springframework.stereotype.Component;

@Component
class LedgerPersistenceAdapter
    implements LedgerRepositoryPort {

    private final SpringDataLedgerEntryRepository repository;

    LedgerPersistenceAdapter(
        SpringDataLedgerEntryRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public DoubleEntryLedger save(
        DoubleEntryLedger ledger
    ) {
        List<LedgerEntryJpaEntity> entities = List.of(
            toEntity(ledger.debitEntry()),
            toEntity(ledger.creditEntry())
        );

        List<LedgerEntryJpaEntity> savedEntities =
            repository.saveAllAndFlush(entities);

        return toDomain(savedEntities);
    }

    private static LedgerEntryJpaEntity toEntity(
        LedgerEntry entry
    ) {
        return new LedgerEntryJpaEntity(
            entry.id(),
            entry.transactionId(),
            entry.walletId(),
            entry.type(),
            entry.amount().amount(),
            entry.amount().currency(),
            entry.createdAt()
        );
    }

    private static DoubleEntryLedger toDomain(
        List<LedgerEntryJpaEntity> entities
    ) {
        if (entities.size() != 2) {
            throw new InvalidLedgerEntryPairException();
        }

        LedgerEntry debitEntry = entities.stream()
            .filter(entity ->
                entity.getEntryType()
                    == LedgerEntryType.DEBIT
            )
            .findFirst()
            .map(LedgerPersistenceAdapter::toDomain)
            .orElseThrow(
                InvalidLedgerEntryPairException::new
            );

        LedgerEntry creditEntry = entities.stream()
            .filter(entity ->
                entity.getEntryType()
                    == LedgerEntryType.CREDIT
            )
            .findFirst()
            .map(LedgerPersistenceAdapter::toDomain)
            .orElseThrow(
                InvalidLedgerEntryPairException::new
            );

        return new DoubleEntryLedger(
            debitEntry,
            creditEntry
        );
    }

    private static LedgerEntry toDomain(
        LedgerEntryJpaEntity entity
    ) {
        return LedgerEntry.rehydrate(
            entity.getId(),
            entity.getTransactionId(),
            entity.getWalletId(),
            entity.getEntryType(),
            new Money(
                entity.getAmount(),
                entity.getCurrency()
            ),
            entity.getCreatedAt()
        );
    }
}
