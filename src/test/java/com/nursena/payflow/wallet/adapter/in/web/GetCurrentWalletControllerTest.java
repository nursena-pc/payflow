package com.nursena.payflow.wallet.adapter.in.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.wallet.application.port.in.GetCurrentWalletResult;
import com.nursena.payflow.wallet.application.port.in.GetCurrentWalletUseCase;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GetCurrentWalletController.class)
@Import({
    SecurityConfiguration.class,
    GetCurrentWalletExceptionHandler.class
})
class GetCurrentWalletControllerTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final UUID WALLET_ID =
        UUID.fromString(
            "63263476-9632-4b65-b986-d9044a72c171"
        );

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-14T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetCurrentWalletUseCase getCurrentWalletUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnCurrentWalletForAuthenticatedUser()
        throws Exception {

        when(
            getCurrentWalletUseCase
                .getCurrentWallet(USER_ID)
        ).thenReturn(
            new GetCurrentWalletResult(
                WALLET_ID,
                new BigDecimal("125.50"),
                Currency.TRY,
                WalletStatus.ACTIVE,
                CREATED_AT
            )
        );

        mockMvc.perform(
                get("/api/v1/wallets/me")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                USER_ID.toString()
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.id")
                    .value(WALLET_ID.toString())
            )
            .andExpect(
                jsonPath("$.balance")
                    .value(125.50)
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

        verify(getCurrentWalletUseCase)
            .getCurrentWallet(USER_ID);
    }

    @Test
    void shouldRejectRequestWithoutAccessToken()
        throws Exception {

        mockMvc.perform(
                get("/api/v1/wallets/me")
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(
            getCurrentWalletUseCase
        );
    }

    @Test
    void shouldReturnNotFoundWhenUserHasNoWallet()
        throws Exception {

        when(
            getCurrentWalletUseCase
                .getCurrentWallet(USER_ID)
        ).thenThrow(
            new WalletNotFoundException()
        );

        mockMvc.perform(
                get("/api/v1/wallets/me")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                USER_ID.toString()
                            )
                        )
                    )
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
                    .value("/api/v1/wallets/me")
            )
            .andExpect(
                jsonPath("$.violations")
                    .isEmpty()
            );

        verify(getCurrentWalletUseCase)
            .getCurrentWallet(USER_ID);
    }
}
