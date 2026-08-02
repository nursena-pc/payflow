package com.nursena.payflow.wallet.adapter.in.web;

import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationConfiguration;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.wallet.application.port.in.TopUpWalletCommand;
import com.nursena.payflow.wallet.application.port.in.TopUpWalletResult;
import com.nursena.payflow.wallet.application.port.in.TopUpWalletUseCase;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.WalletStatus;
import com.nursena.payflow.wallet.domain.exception.WalletConcurrentUpdateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TopUpWalletController.class)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    TopUpWalletExceptionHandler.class
})
class TopUpWalletControllerTest {

    private static final UUID OWNER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final UUID WALLET_ID =
        UUID.fromString(
            "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db"
        );

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-15T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopUpWalletUseCase topUpWalletUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldTopUpAuthenticatedUsersWallet()
        throws Exception {

        when(topUpWalletUseCase.topUp(
            any(TopUpWalletCommand.class)
        )).thenReturn(
            new TopUpWalletResult(
                WALLET_ID,
                new BigDecimal("350.00"),
                Currency.TRY,
                WalletStatus.ACTIVE,
                CREATED_AT
            )
        );

        mockMvc.perform(
                post("/api/v1/wallets/me/top-ups")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "amount": 250.00
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.id")
                    .value(WALLET_ID.toString())
            )
            .andExpect(
                jsonPath("$.balance")
                    .value(350.00)
            )
            .andExpect(
                jsonPath("$.currency")
                    .value("TRY")
            )
            .andExpect(
                jsonPath("$.status")
                    .value("ACTIVE")
            )
            .andExpect(
                jsonPath("$.createdAt")
                    .value(CREATED_AT.toString())
            )
            .andExpect(
                jsonPath("$.ownerId")
                    .doesNotExist()
            );

        verify(topUpWalletUseCase)
            .topUp(
                new TopUpWalletCommand(
                    OWNER_ID,
                    new BigDecimal("250.00")
                )
            );
    }

    @Test
    void shouldRejectRequestWithoutAccessToken()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/wallets/me/top-ups")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "amount": 250.00
                        }
                        """)
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(topUpWalletUseCase);
    }

    @Test
    void shouldReturnNotFoundWhenUserHasNoWallet()
        throws Exception {

        when(topUpWalletUseCase.topUp(
            any(TopUpWalletCommand.class)
        )).thenThrow(
            new WalletNotFoundException()
        );

        mockMvc.perform(
                post("/api/v1/wallets/me/top-ups")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "amount": 25.00
                        }
                        """)
            )
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.status")
                    .value(404)
            )
            .andExpect(
                jsonPath("$.code")
                    .value("WALLET_NOT_FOUND")
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Wallet could not be found."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        "/api/v1/wallets/me/top-ups"
                    )
            );
    }

    @Test
    void shouldRejectZeroAmount()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/wallets/me/top-ups")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "amount": 0.00
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            );

        verifyNoInteractions(topUpWalletUseCase);
    }

    @Test
    void shouldRejectAmountWithTooManyFractionDigits()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/wallets/me/top-ups")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "amount": 10.999
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            );

        verifyNoInteractions(topUpWalletUseCase);
    }

    @Test
    void shouldReturnConflictWhenWalletIsUpdatedConcurrently()
        throws Exception {

        when(topUpWalletUseCase.topUp(
            any(TopUpWalletCommand.class)
        )).thenThrow(
            new WalletConcurrentUpdateException()
        );

        mockMvc.perform(
                post("/api/v1/wallets/me/top-ups")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                    {
                      "amount": 25.00
                    }
                    """)
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.status")
                    .value(409)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "WALLET_CONCURRENT_UPDATE"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Wallet was updated concurrently. "
                            + "Please retry the operation."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        "/api/v1/wallets/me/top-ups"
                    )
            )
            .andExpect(
                jsonPath("$.violations")
                    .isEmpty()
            );
    }

}
