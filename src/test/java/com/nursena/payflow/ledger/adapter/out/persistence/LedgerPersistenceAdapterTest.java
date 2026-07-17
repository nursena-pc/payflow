package com.nursena.payflow.ledger.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.ledger.domain.exception.InvalidLedgerEntryPairException;
import com.nursena.payflow.ledger.domain.model.DoubleEntryLedger;
import com.nursena.payflow.ledger.domain.model.LedgerEntryType;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.stream.StreamSupport;

@ExtendWith(MockitoExtension.class)
class LedgerPersistenceAdapterTest {

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

    private static final UUID DEBIT_ENTRY_ID =
        UUID.fromString(
            "0d7d81e9-2480-4f4b-920d-f51b3635fc92"
        );

    private static final UUID CREDIT_ENTRY_ID =
        UUID.fromString(
            "b2b059b6-8ffb-4ef4-8dc3-808965642059"
        );

    private static final Instant NOW =
        Instant.parse("2026-07-15T17:00:00Z");

    @Mock
    private SpringDataLedgerEntryRepository repository;

    private LedgerPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LedgerPersistenceAdapter(repository);
    }

    @Test
    void shouldSaveAndRestoreEntriesRegardlessOfReturnOrder() {
        DoubleEntryLedger ledger =
            DoubleEntryLedger.forTransfer(
                TRANSACTION_ID,
                SOURCE_WALLET_ID,
                TARGET_WALLET_ID,
                Money.of("125.50", Currency.TRY),
                NOW
            );

        LedgerEntryJpaEntity savedDebit =
            debitEntity();

        LedgerEntryJpaEntity savedCredit =
            creditEntity();

        when(repository.saveAllAndFlush(
            argThat(
                (Iterable<LedgerEntryJpaEntity> entities) ->
                    toList(entities).size() == 2
            )
        )).thenReturn(
            List.of(
                savedCredit,
                savedDebit
            )
        );

        DoubleEntryLedger saved =
            adapter.save(ledger);

        assertThat(saved.debitEntry().id())
            .isEqualTo(DEBIT_ENTRY_ID);

        assertThat(saved.debitEntry().walletId())
            .isEqualTo(SOURCE_WALLET_ID);

        assertThat(saved.debitEntry().type())
            .isEqualTo(LedgerEntryType.DEBIT);

        assertThat(saved.creditEntry().id())
            .isEqualTo(CREDIT_ENTRY_ID);

        assertThat(saved.creditEntry().walletId())
            .isEqualTo(TARGET_WALLET_ID);

        assertThat(saved.creditEntry().type())
            .isEqualTo(LedgerEntryType.CREDIT);

        assertThat(saved.debitEntry().amount())
            .isEqualTo(saved.creditEntry().amount());

        verify(repository).saveAllAndFlush(
            argThat(
                (Iterable<LedgerEntryJpaEntity> entities) -> {
                    List<LedgerEntryJpaEntity> entityList =
                        toList(entities);

                    return entityList.size() == 2
                        && entityList.stream().anyMatch(
                        entity ->
                            entity.getEntryType()
                                == LedgerEntryType.DEBIT
                                && entity.getWalletId()
                                .equals(SOURCE_WALLET_ID)
                    )
                        && entityList.stream().anyMatch(
                        entity ->
                            entity.getEntryType()
                                == LedgerEntryType.CREDIT
                                && entity.getWalletId()
                                .equals(TARGET_WALLET_ID)
                    );
                }
            )
        );
    }

    @Test
    void shouldRejectIncompletePersistenceResult() {
        DoubleEntryLedger ledger =
            DoubleEntryLedger.forTransfer(
                TRANSACTION_ID,
                SOURCE_WALLET_ID,
                TARGET_WALLET_ID,
                Money.of("125.50", Currency.TRY),
                NOW
            );

        when(repository.saveAllAndFlush(
            argThat(
                (Iterable<LedgerEntryJpaEntity> entities) ->
                    toList(entities).size() == 2
            )
        )).thenReturn(
            List.of(debitEntity())
        );

        assertThatThrownBy(() ->
            adapter.save(ledger)
        )
            .isInstanceOf(
                InvalidLedgerEntryPairException.class
            )
            .hasMessage(
                "Ledger entries must form a balanced "
                    + "debit and credit pair."
            );
    }

    private static LedgerEntryJpaEntity debitEntity() {
        return new LedgerEntryJpaEntity(
            DEBIT_ENTRY_ID,
            TRANSACTION_ID,
            SOURCE_WALLET_ID,
            LedgerEntryType.DEBIT,
            Money.of(
                "125.50",
                Currency.TRY
            ).amount(),
            Currency.TRY,
            NOW
        );
    }

    private static LedgerEntryJpaEntity creditEntity() {
        return new LedgerEntryJpaEntity(
            CREDIT_ENTRY_ID,
            TRANSACTION_ID,
            TARGET_WALLET_ID,
            LedgerEntryType.CREDIT,
            Money.of(
                "125.50",
                Currency.TRY
            ).amount(),
            Currency.TRY,
            NOW
        );
    }
    private static List<LedgerEntryJpaEntity> toList(
        Iterable<LedgerEntryJpaEntity> entities
    ) {
        return StreamSupport
            .stream(entities.spliterator(), false)
            .toList();
    }
}
