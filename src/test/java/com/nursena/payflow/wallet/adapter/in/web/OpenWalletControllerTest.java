package com.nursena.payflow.wallet.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.OpenWalletCommand;
import com.nursena.payflow.wallet.application.port.in.OpenWalletResult;
import com.nursena.payflow.wallet.application.port.in.OpenWalletUseCase;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.WalletStatus;
import com.nursena.payflow.configuration.SecurityConfiguration;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OpenWalletController.class)
@Import(SecurityConfiguration.class)
class OpenWalletControllerTest {

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
    private OpenWalletUseCase openWalletUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldOpenWalletForAuthenticatedUser()
        throws Exception {

        OpenWalletResult result =
            new OpenWalletResult(
                WALLET_ID,
                USER_ID,
                new BigDecimal("0.00"),
                Currency.TRY,
                WalletStatus.ACTIVE,
                CREATED_AT
            );

        when(openWalletUseCase.open(
            any(OpenWalletCommand.class)
        )).thenReturn(result);

        mockMvc.perform(
                post("/api/v1/wallets")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                USER_ID.toString()
                            )
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                                        {
                                          "currency": "TRY"
                                        }
                                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.id")
                    .value(WALLET_ID.toString())
            )
            .andExpect(
                jsonPath("$.ownerId")
                    .value(USER_ID.toString())
            )
            .andExpect(
                jsonPath("$.balance")
                    .value(0.00)
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
            );

        verify(openWalletUseCase)
            .open(
                new OpenWalletCommand(
                    USER_ID,
                    Currency.TRY
                )
            );
    }

    @Test
    void shouldRejectRequestWithoutAccessToken()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/wallets")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                                        {
                                          "currency": "TRY"
                                        }
                                        """)
            )
            .andExpect(status().isUnauthorized());
    }
}
