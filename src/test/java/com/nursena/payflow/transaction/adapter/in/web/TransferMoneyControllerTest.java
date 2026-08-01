package com.nursena.payflow.transaction.adapter.in.web;

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
import com.nursena.payflow.transaction.application.port.in.TransferMoneyCommand;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyResult;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyUseCase;
import com.nursena.payflow.transaction.domain.exception.IdempotencyConflictException;
import com.nursena.payflow.transaction.domain.exception.IdempotencyRequestInProgressException;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.wallet.domain.exception.InsufficientBalanceException;
import com.nursena.payflow.wallet.domain.exception.WalletConcurrentUpdateException;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import com.nursena.payflow.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransferMoneyController.class)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    TransferMoneyExceptionHandler.class
})
class TransferMoneyControllerTest {

    private static final UUID OWNER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "b4077781-34f4-466f-8e61-b79ca906bc98"
        );

    private static final UUID SOURCE_WALLET_ID =
        UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
        );

    private static final UUID TARGET_WALLET_ID =
        UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
        );

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-15T18:30:00.123456Z"
        );

    private static final String IDEMPOTENCY_KEY =
        "transfer-request-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferMoneyUseCase transferMoneyUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldCreateAuthenticatedTransfer()
        throws Exception {

        when(transferMoneyUseCase.transfer(
            any(TransferMoneyCommand.class)
        )).thenReturn(successfulResult());

        mockMvc.perform(
                validTransferRequest()
            )
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.transactionId")
                    .value(TRANSACTION_ID.toString())
            )
            .andExpect(
                jsonPath("$.sourceWalletId")
                    .value(SOURCE_WALLET_ID.toString())
            )
            .andExpect(
                jsonPath("$.targetWalletId")
                    .value(TARGET_WALLET_ID.toString())
            )
            .andExpect(
                jsonPath("$.amount")
                    .value(125.50)
            )
            .andExpect(
                jsonPath("$.currency")
                    .value("TRY")
            )
            .andExpect(
                jsonPath("$.status")
                    .value("COMPLETED")
            )
            .andExpect(
                jsonPath("$.createdAt")
                    .value(CREATED_AT.toString())
            )
            .andExpect(
                jsonPath("$.completedAt")
                    .value(CREATED_AT.toString())
            );

        verify(transferMoneyUseCase)
            .transfer(
                new TransferMoneyCommand(
                    OWNER_ID,
                    TARGET_WALLET_ID,
                    new BigDecimal("125.50"),
                    IDEMPOTENCY_KEY
                )
            );
    }

    @Test
    void shouldRejectRequestWithoutAccessToken()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/transfers")
                    .header(
                        "Idempotency-Key",
                        IDEMPOTENCY_KEY
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(validRequestBody())
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(transferMoneyUseCase);
    }

    @Test
    void shouldRejectMissingIdempotencyKey()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/transfers")
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
                    .content(validRequestBody())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("MISSING_IDEMPOTENCY_KEY")
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Idempotency-Key header is required."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/transfers")
            );

        verifyNoInteractions(transferMoneyUseCase);
    }

    @Test
    void shouldRejectZeroAmount()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/transfers")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        IDEMPOTENCY_KEY
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "targetWalletId":
                            "%s",
                          "amount": 0.00
                        }
                        """.formatted(
                            TARGET_WALLET_ID
                        )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            );

        verifyNoInteractions(transferMoneyUseCase);
    }

    @Test
    void shouldReturnNotFoundWhenWalletIsMissing()
        throws Exception {

        when(transferMoneyUseCase.transfer(
            any(TransferMoneyCommand.class)
        )).thenThrow(
            new WalletNotFoundException()
        );

        mockMvc.perform(validTransferRequest())
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
            );
    }

    @Test
    void shouldReturnConflictForReusedKeyWithDifferentPayload()
        throws Exception {

        when(transferMoneyUseCase.transfer(
            any(TransferMoneyCommand.class)
        )).thenThrow(
            new IdempotencyConflictException()
        );

        mockMvc.perform(validTransferRequest())
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value("IDEMPOTENCY_KEY_CONFLICT")
            );
    }

    @Test
    void shouldReturnConflictWhileRequestIsInProgress()
        throws Exception {

        when(transferMoneyUseCase.transfer(
            any(TransferMoneyCommand.class)
        )).thenThrow(
            new IdempotencyRequestInProgressException()
        );

        mockMvc.perform(validTransferRequest())
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "IDEMPOTENCY_REQUEST_IN_PROGRESS"
                    )
            );
    }

    @Test
    void shouldReturnConflictForConcurrentWalletUpdate()
        throws Exception {

        when(transferMoneyUseCase.transfer(
            any(TransferMoneyCommand.class)
        )).thenThrow(
            new WalletConcurrentUpdateException()
        );

        mockMvc.perform(validTransferRequest())
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value("WALLET_CONCURRENT_UPDATE")
            );
    }

    @Test
    void shouldReturnUnprocessableEntityForInsufficientBalance()
        throws Exception {

        when(transferMoneyUseCase.transfer(
            any(TransferMoneyCommand.class)
        )).thenThrow(
            new InsufficientBalanceException()
        );

        mockMvc.perform(validTransferRequest())
            .andExpect(
                status().isUnprocessableEntity()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("INSUFFICIENT_BALANCE")
            );
    }

    private org.springframework.test.web.servlet
        .request.MockHttpServletRequestBuilder
    validTransferRequest() {

        return post("/api/v1/transfers")
            .with(
                jwt().jwt(token ->
                    token.subject(
                        OWNER_ID.toString()
                    )
                )
            )
            .header(
                "Idempotency-Key",
                IDEMPOTENCY_KEY
            )
            .contentType(
                MediaType.APPLICATION_JSON
            )
            .content(validRequestBody());
    }

    private static String validRequestBody() {
        return """
            {
              "targetWalletId": "%s",
              "amount": 125.50
            }
            """.formatted(
            TARGET_WALLET_ID
        );
    }

    private static TransferMoneyResult successfulResult() {
        return new TransferMoneyResult(
            TRANSACTION_ID,
            SOURCE_WALLET_ID,
            TARGET_WALLET_ID,
            new BigDecimal("125.50"),
            Currency.TRY,
            TransactionStatus.COMPLETED,
            CREATED_AT,
            CREATED_AT
        );
    }
}
