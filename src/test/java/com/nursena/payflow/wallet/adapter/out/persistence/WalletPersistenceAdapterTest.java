package com.nursena.payflow.wallet.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.nursena.payflow.wallet.domain.exception.WalletAlreadyExistsException;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.Money;
import com.nursena.payflow.wallet.domain.model.Wallet;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.util.Optional;

import com.nursena.payflow.wallet.domain.model.WalletStatus;
@ExtendWith(MockitoExtension.class)
class WalletPersistenceAdapterTest {

    private static final UUID OWNER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final Instant NOW =
        Instant.parse("2026-07-14T12:00:00Z");

    @Mock
    private SpringDataWalletRepository repository;

    private WalletPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

        adapter = new WalletPersistenceAdapter(
            repository,
            clock
        );
    }

    @Test
    void shouldCheckWalletExistenceByOwnerId() {
        when(repository.existsByOwnerId(OWNER_ID))
            .thenReturn(true);

        boolean exists =
            adapter.existsByOwnerId(OWNER_ID);

        assertThat(exists).isTrue();

        verify(repository)
            .existsByOwnerId(OWNER_ID);
    }

    @Test
    void shouldSaveAndRestoreWallet() {
        Wallet wallet = Wallet.open(
            OWNER_ID,
            Currency.TRY,
            NOW
        );

        when(repository.saveAndFlush(
            any(WalletJpaEntity.class)
        )).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        Wallet savedWallet = adapter.save(wallet);

        assertThat(savedWallet.id())
            .isEqualTo(wallet.id());

        assertThat(savedWallet.ownerId())
            .isEqualTo(OWNER_ID);

        assertThat(savedWallet.balance())
            .isEqualTo(wallet.balance());

        assertThat(savedWallet.status())
            .isEqualTo(wallet.status());

        assertThat(savedWallet.createdAt())
            .isEqualTo(NOW);

        verify(repository)
            .saveAndFlush(
                any(WalletJpaEntity.class)
            );
    }

    @Test
    void shouldTranslateDuplicateOwnerConstraintViolation() {
        Wallet wallet = Wallet.open(
            OWNER_ID,
            Currency.TRY,
            NOW
        );

        ConstraintViolationException violation =
            new ConstraintViolationException(
                "duplicate wallet owner",
                new SQLException(),
                "uq_wallets_owner_id"
            );

        when(repository.saveAndFlush(
            any(WalletJpaEntity.class)
        )).thenThrow(
            new DataIntegrityViolationException(
                "duplicate wallet owner",
                violation
            )
        );

        assertThatThrownBy(() -> adapter.save(wallet))
            .isInstanceOf(
                WalletAlreadyExistsException.class
            )
            .hasMessage(
                "User already has a wallet."
            );
    }

    @Test
    void shouldNotTranslateUnrelatedConstraintViolation() {
        Wallet wallet = Wallet.open(
            OWNER_ID,
            Currency.TRY,
            NOW
        );

        ConstraintViolationException violation =
            new ConstraintViolationException(
                "unrelated constraint",
                new SQLException(),
                "some_other_constraint"
            );

        DataIntegrityViolationException databaseException =
            new DataIntegrityViolationException(
                "unrelated constraint",
                violation
            );

        when(repository.saveAndFlush(
            any(WalletJpaEntity.class)
        )).thenThrow(databaseException);

        assertThatThrownBy(() -> adapter.save(wallet))
            .isSameAs(databaseException);
    }

    @Test
    void shouldFindWalletByOwnerId() {
        UUID walletId = UUID.fromString(
            "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db"
        );

        WalletJpaEntity entity = new WalletJpaEntity(
            walletId,
            OWNER_ID,
            new BigDecimal("125.50"),
            Currency.TRY,
            WalletStatus.ACTIVE,
            NOW,
            NOW
        );

        when(repository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(entity));

        Optional<Wallet> result =
            adapter.findByOwnerId(OWNER_ID);

        assertThat(result).isPresent();

        Wallet wallet = result.orElseThrow();

        assertThat(wallet.id())
            .isEqualTo(walletId);

        assertThat(wallet.ownerId())
            .isEqualTo(OWNER_ID);

        assertThat(wallet.balance().amount())
            .isEqualByComparingTo(
                new BigDecimal("125.50")
            );

        assertThat(wallet.balance().currency())
            .isEqualTo(Currency.TRY);

        assertThat(wallet.status())
            .isEqualTo(WalletStatus.ACTIVE);

        assertThat(wallet.createdAt())
            .isEqualTo(NOW);

        verify(repository)
            .findByOwnerId(OWNER_ID);
    }

    @Test
    void shouldReturnEmptyWhenWalletDoesNotExist() {
        when(repository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.empty());

        Optional<Wallet> result =
            adapter.findByOwnerId(OWNER_ID);

        assertThat(result).isEmpty();

        verify(repository)
            .findByOwnerId(OWNER_ID);
    }

    @Test
    void shouldUpdateManagedWalletEntity() {
        UUID walletId = UUID.fromString(
            "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db"
        );

        WalletJpaEntity entity = new WalletJpaEntity(
            walletId,
            OWNER_ID,
            new BigDecimal("100.00"),
            Currency.TRY,
            WalletStatus.ACTIVE,
            NOW,
            NOW
        );

        Wallet wallet = Wallet.rehydrate(
            walletId,
            OWNER_ID,
            Money.of("250.00", Currency.TRY),
            WalletStatus.ACTIVE,
            NOW
        );

        when(repository.findById(walletId))
            .thenReturn(Optional.of(entity));

        Wallet updated = adapter.update(wallet);

        assertThat(updated.balance().amount())
            .isEqualByComparingTo(
                new BigDecimal("250.00")
            );

        assertThat(updated.balance().currency())
            .isEqualTo(Currency.TRY);

        verify(repository)
            .findById(walletId);

        verify(repository)
            .flush();
    }

}
