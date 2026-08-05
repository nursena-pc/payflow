package com.nursena.payflow.transaction.integration;

import static com.nursena.payflow.user.support.EmailVerificationTestSupport.markVerified;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransferMoneyHttpIntegrationTest {

    private static final BigDecimal INITIAL_SOURCE_BALANCE =
        new BigDecimal("300.00");

    private static final BigDecimal TRANSFER_AMOUNT =
        new BigDecimal("125.50");

    private static final BigDecimal EXPECTED_SOURCE_BALANCE =
        new BigDecimal("174.50");

    private static final BigDecimal EXPECTED_TARGET_BALANCE =
        new BigDecimal("125.50");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldTransferReplayAndRejectConflictingPayload()
        throws Exception {

        String password = "StrongPassword123!";

        String sourceEmail =
            uniqueEmail("http-transfer-source");

        String targetEmail =
            uniqueEmail("http-transfer-target");

        registerUser(sourceEmail, password);
        registerUser(targetEmail, password);

        String sourceAccessToken =
            authenticateUser(
                sourceEmail,
                password
            );

        String targetAccessToken =
            authenticateUser(
                targetEmail,
                password
            );

        WalletInfo sourceWallet =
            openWallet(sourceAccessToken);

        WalletInfo targetWallet =
            openWallet(targetAccessToken);

        topUpWallet(
            sourceAccessToken,
            INITIAL_SOURCE_BALANCE
        );

        String idempotencyKey =
            "http-transfer-" + UUID.randomUUID();

        MvcResult firstTransferResult =
            transfer(
                sourceAccessToken,
                idempotencyKey,
                targetWallet.id(),
                TRANSFER_AMOUNT
            )
                .andExpect(status().isCreated())
                .andExpect(
                    jsonPath("$.transactionId")
                        .isNotEmpty()
                )
                .andExpect(
                    jsonPath("$.sourceWalletId")
                        .value(
                            sourceWallet.id().toString()
                        )
                )
                .andExpect(
                    jsonPath("$.targetWalletId")
                        .value(
                            targetWallet.id().toString()
                        )
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
                        .isNotEmpty()
                )
                .andExpect(
                    jsonPath("$.completedAt")
                        .isNotEmpty()
                )
                .andReturn();

        JsonNode firstResponse =
            responseBody(firstTransferResult);

        UUID transactionId = UUID.fromString(
            firstResponse
                .get("transactionId")
                .asText()
        );

        assertPersistedTransfer(
            transactionId,
            sourceWallet.id(),
            targetWallet.id(),
            idempotencyKey
        );

        MvcResult replayResult =
            transfer(
                sourceAccessToken,
                idempotencyKey,
                targetWallet.id(),
                TRANSFER_AMOUNT
            )
                .andExpect(status().isCreated())
                .andExpect(
                    jsonPath("$.transactionId")
                        .value(
                            transactionId.toString()
                        )
                )
                .andExpect(
                    jsonPath("$.status")
                        .value("COMPLETED")
                )
                .andReturn();

        JsonNode replayResponse =
            responseBody(replayResult);

        assertThat(
            replayResponse
                .get("createdAt")
                .asText()
        ).isEqualTo(
            firstResponse
                .get("createdAt")
                .asText()
        );

        assertThat(
            replayResponse
                .get("completedAt")
                .asText()
        ).isEqualTo(
            firstResponse
                .get("completedAt")
                .asText()
        );

        assertPersistedTransfer(
            transactionId,
            sourceWallet.id(),
            targetWallet.id(),
            idempotencyKey
        );

        transfer(
            sourceAccessToken,
            idempotencyKey,
            targetWallet.id(),
            new BigDecimal("100.00")
        )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.status")
                    .value(409)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "IDEMPOTENCY_KEY_CONFLICT"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Idempotency key has already been used "
                            + "for another transfer request."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/transfers")
            )
            .andExpect(
                jsonPath("$.violations")
                    .isEmpty()
            );

        assertPersistedTransfer(
            transactionId,
            sourceWallet.id(),
            targetWallet.id(),
            idempotencyKey
        );
    }

    private void assertPersistedTransfer(
        UUID transactionId,
        UUID sourceWalletId,
        UUID targetWalletId,
        String idempotencyKey
    ) {
        assertThat(
            walletBalance(sourceWalletId)
        ).isEqualByComparingTo(
            EXPECTED_SOURCE_BALANCE
        );

        assertThat(
            walletBalance(targetWalletId)
        ).isEqualByComparingTo(
            EXPECTED_TARGET_BALANCE
        );

        Long transactionCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM payment_transactions
                WHERE source_wallet_id = ?
                  AND idempotency_key = ?
                """,
                Long.class,
                sourceWalletId,
                idempotencyKey
            );

        assertThat(transactionCount)
            .isEqualTo(1L);

        String transactionStatus =
            jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM payment_transactions
                WHERE id = ?
                """,
                String.class,
                transactionId
            );

        assertThat(transactionStatus)
            .isEqualTo("COMPLETED");

        String transactionType =
            jdbcTemplate.queryForObject(
                """
                SELECT transaction_type
                FROM payment_transactions
                WHERE id = ?
                """,
                String.class,
                transactionId
            );

        assertThat(transactionType)
            .isEqualTo("TRANSFER");

        Boolean hasCompletionTimestamp =
            jdbcTemplate.queryForObject(
                """
                SELECT completed_at IS NOT NULL
                FROM payment_transactions
                WHERE id = ?
                """,
                Boolean.class,
                transactionId
            );

        assertThat(hasCompletionTimestamp)
            .isTrue();

        Long ledgerCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ledger_entries
                WHERE transaction_id = ?
                """,
                Long.class,
                transactionId
            );

        assertThat(ledgerCount)
            .isEqualTo(2L);

        assertLedgerEntry(
            transactionId,
            sourceWalletId,
            "DEBIT"
        );

        assertLedgerEntry(
            transactionId,
            targetWalletId,
            "CREDIT"
        );
    }

    private void assertLedgerEntry(
        UUID transactionId,
        UUID walletId,
        String entryType
    ) {
        Long entryCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ledger_entries
                WHERE transaction_id = ?
                  AND wallet_id = ?
                  AND entry_type = ?
                  AND amount = ?
                  AND currency = 'TRY'
                """,
                Long.class,
                transactionId,
                walletId,
                entryType,
                TRANSFER_AMOUNT
            );

        assertThat(entryCount)
            .isEqualTo(1L);
    }

    private BigDecimal walletBalance(
        UUID walletId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT balance
            FROM wallets
            WHERE id = ?
            """,
            BigDecimal.class,
            walletId
        );
    }

    private void registerUser(
        String email,
        String password
    ) throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            new RegistrationRequest(
                                email,
                                password
                            )
                        )
                    )
            )
            .andExpect(status().isCreated());

        markVerified(jdbcTemplate, email);
    }

    private String authenticateUser(
        String email,
        String password
    ) throws Exception {

        MvcResult result = mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginRequest(
                                email,
                                password
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.accessToken")
                    .isNotEmpty()
            )
            .andExpect(
                jsonPath("$.tokenType")
                    .value("Bearer")
            )
            .andReturn();

        return responseBody(result)
            .get("accessToken")
            .asText();
    }

    private WalletInfo openWallet(
        String accessToken
    ) throws Exception {

        MvcResult result = mockMvc.perform(
                post("/api/v1/wallets")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(accessToken)
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
            .andReturn();

        JsonNode response =
            responseBody(result);

        return new WalletInfo(
            UUID.fromString(
                response.get("id").asText()
            ),
            UUID.fromString(
                response
                    .get("ownerId")
                    .asText()
            )
        );
    }

    private void topUpWallet(
        String accessToken,
        BigDecimal amount
    ) throws Exception {

        mockMvc.perform(
                post("/api/v1/wallets/me/top-ups")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(accessToken)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            new TopUpRequest(amount)
                        )
                    )
            )
            .andExpect(status().isOk());
    }

    private ResultActions transfer(
        String accessToken,
        String idempotencyKey,
        UUID targetWalletId,
        BigDecimal amount
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/transfers")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    bearer(accessToken)
                )
                .header(
                    "Idempotency-Key",
                    idempotencyKey
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    objectMapper.writeValueAsString(
                        new TransferRequest(
                            targetWalletId,
                            amount
                        )
                    )
                )
        );
    }

    private JsonNode responseBody(
        MvcResult result
    ) throws Exception {

        return objectMapper.readTree(
            result
                .getResponse()
                .getContentAsString()
        );
    }

    private static String bearer(
        String accessToken
    ) {
        return "Bearer " + accessToken;
    }

    private static String uniqueEmail(
        String prefix
    ) {
        return prefix
            + "-"
            + UUID.randomUUID()
            + "@example.com";
    }

    private record WalletInfo(
        UUID id,
        UUID ownerId
    ) {
    }

    private record RegistrationRequest(
        String email,
        String password
    ) {
    }

    private record LoginRequest(
        String email,
        String password
    ) {
    }

    private record TopUpRequest(
        BigDecimal amount
    ) {
    }

    private record TransferRequest(
        UUID targetWalletId,
        BigDecimal amount
    ) {
    }
}
