package com.nursena.payflow.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.OpenWalletCommand;
import com.nursena.payflow.wallet.application.port.in.OpenWalletResult;
import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.exception.WalletAlreadyExistsException;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.Wallet;
import com.nursena.payflow.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenWalletServiceTest {

    private static final UUID OWNER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final Instant NOW =
        Instant.parse("2026-07-14T12:00:00Z");

    @Mock
    private WalletRepositoryPort walletRepository;

    private OpenWalletService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

        service = new OpenWalletService(
            walletRepository,
            clock
        );
    }

    @Test
    void shouldOpenWalletWithZeroBalance() {
        when(walletRepository.existsByOwnerId(OWNER_ID))
            .thenReturn(false);

        when(walletRepository.save(any(Wallet.class)))
            .thenAnswer(invocation ->
                invocation.getArgument(0)
            );

        OpenWalletResult result = service.open(
            new OpenWalletCommand(
                OWNER_ID,
                Currency.TRY
            )
        );

        assertThat(result.id()).isNotNull();
        assertThat(result.ownerId()).isEqualTo(OWNER_ID);
        assertThat(result.balance())
            .isEqualByComparingTo(
                new BigDecimal("0.00")
            );
        assertThat(result.currency())
            .isEqualTo(Currency.TRY);
        assertThat(result.status())
            .isEqualTo(WalletStatus.ACTIVE);
        assertThat(result.createdAt()).isEqualTo(NOW);

        verify(walletRepository)
            .existsByOwnerId(OWNER_ID);

        verify(walletRepository)
            .save(any(Wallet.class));
    }

    @Test
    void shouldRejectOwnerWhoAlreadyHasWallet() {
        when(walletRepository.existsByOwnerId(OWNER_ID))
            .thenReturn(true);

        assertThatThrownBy(() ->
            service.open(
                new OpenWalletCommand(
                    OWNER_ID,
                    Currency.TRY
                )
            )
        )
            .isInstanceOf(
                WalletAlreadyExistsException.class
            )
            .hasMessage(
                "User already has a wallet."
            );

        verify(walletRepository)
            .existsByOwnerId(OWNER_ID);

        verify(walletRepository, never())
            .save(any(Wallet.class));
    }
}
