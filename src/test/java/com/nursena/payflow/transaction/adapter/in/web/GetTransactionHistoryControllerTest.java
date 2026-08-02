package com.nursena.payflow.transaction.adapter.in.web;

import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationConfiguration;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.transaction.application.model.TransactionDirection;
import com.nursena.payflow.transaction.application.model.TransactionHistoryItem;
import com.nursena.payflow.transaction.application.model.TransactionHistoryPage;
import com.nursena.payflow.transaction.application.port.in.GetTransactionHistoryQuery;
import com.nursena.payflow.transaction.application.port.in.GetTransactionHistoryUseCase;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.transaction.domain.model.TransactionType;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.transaction.application.model.TransactionHistoryFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GetTransactionHistoryController.class)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    TransactionHistoryExceptionHandler.class
})
class GetTransactionHistoryControllerTest {

    private static final UUID OWNER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "b4077781-34f4-466f-8e61-b79ca906bc98"
        );

    private static final UUID COUNTERPARTY_WALLET_ID =
        UUID.fromString(
            "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db"
        );

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-16T10:00:00Z"
        );

    private static final Instant COMPLETED_AT =
        Instant.parse(
            "2026-07-16T10:00:01Z"
        );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetTransactionHistoryUseCase
        getTransactionHistoryUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnAuthenticatedUsersTransactionHistory()
        throws Exception {

        GetTransactionHistoryQuery expectedQuery =
            new GetTransactionHistoryQuery(
                OWNER_ID,
                0,
                20
            );

        when(
            getTransactionHistoryUseCase
                .getTransactionHistory(expectedQuery)
        ).thenReturn(historyPage());

        mockMvc.perform(
                get("/api/v1/transactions/me")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.items.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.items[0].transactionId")
                    .value(TRANSACTION_ID.toString())
            )
            .andExpect(
                jsonPath("$.items[0].type")
                    .value("TRANSFER")
            )
            .andExpect(
                jsonPath("$.items[0].direction")
                    .value("OUTGOING")
            )
            .andExpect(
                jsonPath(
                    "$.items[0].counterpartyWalletId"
                )
                    .value(
                        COUNTERPARTY_WALLET_ID.toString()
                    )
            )
            .andExpect(
                jsonPath("$.items[0].amount")
                    .value(125.50)
            )
            .andExpect(
                jsonPath("$.items[0].currency")
                    .value("TRY")
            )
            .andExpect(
                jsonPath("$.items[0].status")
                    .value("COMPLETED")
            )
            .andExpect(
                jsonPath("$.items[0].createdAt")
                    .value(CREATED_AT.toString())
            )
            .andExpect(
                jsonPath("$.items[0].completedAt")
                    .value(COMPLETED_AT.toString())
            )
            .andExpect(
                jsonPath("$.items[0].idempotencyKey")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.page")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.size")
                    .value(20)
            )
            .andExpect(
                jsonPath("$.totalElements")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.totalPages")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.first")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.last")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.hasNext")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.hasPrevious")
                    .value(false)
            );

        verify(getTransactionHistoryUseCase)
            .getTransactionHistory(expectedQuery);
    }

    @Test
    void shouldApplyRequestedPagination()
        throws Exception {

        GetTransactionHistoryQuery expectedQuery =
            new GetTransactionHistoryQuery(
                OWNER_ID,
                2,
                10
            );

        when(
            getTransactionHistoryUseCase
                .getTransactionHistory(expectedQuery)
        ).thenReturn(
            new TransactionHistoryPage(
                List.of(),
                2,
                10,
                21,
                3
            )
        );

        mockMvc.perform(
                get("/api/v1/transactions/me")
                    .param("page", "2")
                    .param("size", "10")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.items.length()")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.page")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.size")
                    .value(10)
            )
            .andExpect(
                jsonPath("$.totalElements")
                    .value(21)
            )
            .andExpect(
                jsonPath("$.totalPages")
                    .value(3)
            )
            .andExpect(
                jsonPath("$.first")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.last")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.hasNext")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.hasPrevious")
                    .value(true)
            );

        verify(getTransactionHistoryUseCase)
            .getTransactionHistory(expectedQuery);
    }

    @Test
    void shouldApplyRequestedFilters()
        throws Exception {

        Instant from =
            Instant.parse(
                "2026-07-01T00:00:00Z"
            );

        Instant to =
            Instant.parse(
                "2026-08-01T00:00:00Z"
            );

        TransactionHistoryFilter filter =
            new TransactionHistoryFilter(
                TransactionDirection.OUTGOING,
                TransactionStatus.COMPLETED,
                from,
                to
            );

        GetTransactionHistoryQuery expectedQuery =
            new GetTransactionHistoryQuery(
                OWNER_ID,
                0,
                20,
                filter
            );

        when(
            getTransactionHistoryUseCase
                .getTransactionHistory(expectedQuery)
        ).thenReturn(
            new TransactionHistoryPage(
                List.of(),
                0,
                20,
                0,
                0
            )
        );

        mockMvc.perform(
                get("/api/v1/transactions/me")
                    .param(
                        "direction",
                        "OUTGOING"
                    )
                    .param(
                        "status",
                        "COMPLETED"
                    )
                    .param(
                        "from",
                        from.toString()
                    )
                    .param(
                        "to",
                        to.toString()
                    )
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.items.length()")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.page")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.size")
                    .value(20)
            );

        verify(getTransactionHistoryUseCase)
            .getTransactionHistory(expectedQuery);
    }

    @Test
    void shouldAllowEqualDateBoundaries()
        throws Exception {

        Instant boundary =
            Instant.parse(
                "2026-07-01T00:00:00Z"
            );

        TransactionHistoryFilter filter =
            new TransactionHistoryFilter(
                null,
                null,
                boundary,
                boundary
            );

        GetTransactionHistoryQuery expectedQuery =
            new GetTransactionHistoryQuery(
                OWNER_ID,
                0,
                20,
                filter
            );

        when(
            getTransactionHistoryUseCase
                .getTransactionHistory(expectedQuery)
        ).thenReturn(
            new TransactionHistoryPage(
                List.of(),
                0,
                20,
                0,
                0
            )
        );

        mockMvc.perform(
                get("/api/v1/transactions/me")
                    .param(
                        "from",
                        boundary.toString()
                    )
                    .param(
                        "to",
                        boundary.toString()
                    )
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
            )
            .andExpect(status().isOk());

        verify(getTransactionHistoryUseCase)
            .getTransactionHistory(expectedQuery);
    }

    @Test
    void shouldRejectRequestWithoutAccessToken()
        throws Exception {

        mockMvc.perform(
                get("/api/v1/transactions/me")
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(
            getTransactionHistoryUseCase
        );
    }

    @Test
    void shouldRejectReversedDateRange()
        throws Exception {

        mockMvc.perform(
                get("/api/v1/transactions/me")
                    .param(
                        "from",
                        "2026-08-01T00:00:00Z"
                    )
                    .param(
                        "to",
                        "2026-07-01T00:00:00Z"
                    )
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.status")
                    .value(400)
            )
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Request validation failed."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        "/api/v1/transactions/me"
                    )
            );

        verifyNoInteractions(
            getTransactionHistoryUseCase
        );
    }

    @ParameterizedTest
    @CsvSource({
        "direction, SIDEWAYS",
        "status, UNKNOWN",
        "from, not-an-instant",
        "to, not-an-instant"
    })
    void shouldRejectInvalidFilterValues(
        String parameter,
        String value
    ) throws Exception {

        mockMvc.perform(
                get("/api/v1/transactions/me")
                    .param(parameter, value)
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.status")
                    .value(400)
            )
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Request validation failed."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        "/api/v1/transactions/me"
                    )
            );

        verifyNoInteractions(
            getTransactionHistoryUseCase
        );
    }

    @ParameterizedTest
    @CsvSource({
        "-1, 20",
        "0, 0",
        "0, 101"
    })
    void shouldRejectInvalidPagination(
        int page,
        int size
    ) throws Exception {

        mockMvc.perform(
                get("/api/v1/transactions/me")
                    .param(
                        "page",
                        Integer.toString(page)
                    )
                    .param(
                        "size",
                        Integer.toString(size)
                    )
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.status")
                    .value(400)
            )
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Request validation failed."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        "/api/v1/transactions/me"
                    )
            );

        verifyNoInteractions(
            getTransactionHistoryUseCase
        );
    }

    @Test
    void shouldRejectNonNumericPagination()
        throws Exception {

        mockMvc.perform(
                get("/api/v1/transactions/me")
                    .param("page", "invalid")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
                            )
                        )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            );

        verifyNoInteractions(
            getTransactionHistoryUseCase
        );
    }

    @Test
    void shouldReturnNotFoundWhenUserHasNoWallet()
        throws Exception {

        GetTransactionHistoryQuery query =
            new GetTransactionHistoryQuery(
                OWNER_ID,
                0,
                20
            );

        when(
            getTransactionHistoryUseCase
                .getTransactionHistory(query)
        ).thenThrow(
            new WalletNotFoundException()
        );

        mockMvc.perform(
                get("/api/v1/transactions/me")
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                OWNER_ID.toString()
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
                    .value(
                        "/api/v1/transactions/me"
                    )
            );

        verify(getTransactionHistoryUseCase)
            .getTransactionHistory(query);
    }

    private static TransactionHistoryPage historyPage() {
        TransactionHistoryItem item =
            new TransactionHistoryItem(
                TRANSACTION_ID,
                TransactionType.TRANSFER,
                TransactionDirection.OUTGOING,
                COUNTERPARTY_WALLET_ID,
                new BigDecimal("125.50"),
                Currency.TRY,
                TransactionStatus.COMPLETED,
                CREATED_AT,
                COMPLETED_AT
            );

        return new TransactionHistoryPage(
            List.of(item),
            0,
            20,
            1,
            1
        );
    }
}
