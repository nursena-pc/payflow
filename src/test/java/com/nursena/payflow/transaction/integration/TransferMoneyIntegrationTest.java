package com.nursena.payflow.transaction.integration;

import static com.nursena.payflow.user.support.EmailVerificationTestSupport.markVerified;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyCommand;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyResult;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyUseCase;
import com.nursena.payflow.transaction.domain.exception.IdempotencyConflictException;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransferMoneyIntegrationTest {

    private static final BigDecimal INITIAL_SOURCE_BALANCE =
        new BigDecimal("300.00");

    private static final BigDecimal TRANSFER_AMOUNT =
        new BigDecimal("125.50");

    private static final BigDecimal EXPECTED_SOURCE_BALANCE =
        new BigDecimal("174.50");

    private static final BigDecimal EXPECTED_TARGET_BALANCE =
        new BigDecimal("125.50");

    private static final String IDEMPOTENCY_KEY =
        "transfer-integration-request-1";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransferMoneyUseCase transferMoneyUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistTransferLedgerAndReplayIdempotently()
        throws Exception {

        String password = "StrongPassword123!";

        String sourceEmail =
            uniqueEmail("transfer-source");

        String targetEmail =
            uniqueEmail("transfer-target");

        registerUser(sourceEmail, password);
        registerUser(targetEmail, password);

        String sourceAccessToken =
            authenticateUser(sourceEmail, password);

        String targetAccessToken =
            authenticateUser(targetEmail, password);

        WalletInfo sourceWallet =
            openWallet(sourceAccessToken);

        WalletInfo targetWallet =
            openWallet(targetAccessToken);

        topUpWallet(
            sourceAccessToken,
            INITIAL_SOURCE_BALANCE
        );

        TransferMoneyCommand command =
            new TransferMoneyCommand(
                sourceWallet.ownerId(),
                targetWallet.id(),
                TRANSFER_AMOUNT,
                IDEMPOTENCY_KEY
            );

        TransferMoneyResult firstResult =
            transferMoneyUseCase.transfer(command);

        assertThat(firstResult.transactionId())
            .isNotNull();

        assertThat(firstResult.sourceWalletId())
            .isEqualTo(sourceWallet.id());

        assertThat(firstResult.targetWalletId())
            .isEqualTo(targetWallet.id());

        assertThat(firstResult.amount())
            .isEqualByComparingTo(TRANSFER_AMOUNT);

        assertThat(firstResult.currency().name())
            .isEqualTo("TRY");

        assertThat(firstResult.status())
            .isEqualTo(TransactionStatus.COMPLETED);

        assertThat(firstResult.createdAt())
            .isNotNull();

        assertThat(firstResult.completedAt())
            .isNotNull();

        assertPersistedState(
            firstResult.transactionId(),
            sourceWallet.id(),
            targetWallet.id()
        );

        TransferMoneyResult replayResult =
            transferMoneyUseCase.transfer(command);

        assertThat(replayResult.transactionId())
            .isEqualTo(firstResult.transactionId());

        assertThat(replayResult.status())
            .isEqualTo(TransactionStatus.COMPLETED);

        assertThat(replayResult.amount())
            .isEqualByComparingTo(TRANSFER_AMOUNT);

        assertThat(replayResult.createdAt())
            .isEqualTo(firstResult.createdAt());

        assertThat(replayResult.completedAt())
            .isEqualTo(firstResult.completedAt());

        assertPersistedState(
            firstResult.transactionId(),
            sourceWallet.id(),
            targetWallet.id()
        );

        TransferMoneyCommand conflictingCommand =
            new TransferMoneyCommand(
                sourceWallet.ownerId(),
                targetWallet.id(),
                new BigDecimal("100.00"),
                IDEMPOTENCY_KEY
            );

        assertThatThrownBy(() ->
            transferMoneyUseCase.transfer(
                conflictingCommand
            )
        )
            .isInstanceOf(
                IdempotencyConflictException.class
            )
            .hasMessage(
                "Idempotency key has already been used "
                    + "for another transfer request."
            );

        assertPersistedState(
            firstResult.transactionId(),
            sourceWallet.id(),
            targetWallet.id()
        );
    }

    private void assertPersistedState(
        UUID transactionId,
        UUID sourceWalletId,
        UUID targetWalletId
    ) throws Exception {
        assertThat(walletBalance(sourceWalletId))
            .isEqualByComparingTo(
                EXPECTED_SOURCE_BALANCE
            );

        assertThat(walletBalance(targetWalletId))
            .isEqualByComparingTo(
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
                IDEMPOTENCY_KEY
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
        assertOutboxEvent(
            transactionId,
            sourceWalletId,
            targetWalletId
        );
        Long ledgerEntryCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ledger_entries
                WHERE transaction_id = ?
                """,
                Long.class,
                transactionId
            );

        assertThat(ledgerEntryCount)
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
                """,
                Long.class,
                transactionId,
                walletId,
                entryType
            );

        assertThat(entryCount)
            .isEqualTo(1L);

        BigDecimal storedAmount =
            jdbcTemplate.queryForObject(
                """
                SELECT amount
                FROM ledger_entries
                WHERE transaction_id = ?
                  AND wallet_id = ?
                  AND entry_type = ?
                """,
                BigDecimal.class,
                transactionId,
                walletId,
                entryType
            );

        assertThat(storedAmount)
            .isEqualByComparingTo(TRANSFER_AMOUNT);

        String storedCurrency =
            jdbcTemplate.queryForObject(
                """
                SELECT currency
                FROM ledger_entries
                WHERE transaction_id = ?
                  AND wallet_id = ?
                  AND entry_type = ?
                """,
                String.class,
                transactionId,
                walletId,
                entryType
            );

        assertThat(storedCurrency)
            .isEqualTo("TRY");
    }

    private BigDecimal walletBalance(UUID walletId) {
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
            .andReturn();

        JsonNode response = objectMapper.readTree(
            result
                .getResponse()
                .getContentAsString()
        );

        return response
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

        JsonNode response = objectMapper.readTree(
            result
                .getResponse()
                .getContentAsString()
        );

        return new WalletInfo(
            UUID.fromString(
                response.get("id").asText()
            ),
            UUID.fromString(
                response.get("ownerId").asText()
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
    private void assertOutboxEvent(
        UUID transactionId,
        UUID sourceWalletId,
        UUID targetWalletId
    ) throws Exception {

        Long outboxCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                WHERE aggregate_type = 'PAYMENT_TRANSACTION'
                  AND aggregate_id = ?
                  AND event_type = 'wallet.transfer.completed'
                  AND event_version = 1
                """,
                Long.class,
                transactionId
            );

        assertThat(outboxCount)
            .isEqualTo(1L);

        OutboxMetadata metadata =
            jdbcTemplate.queryForObject(
                """
                SELECT
                    outbox.id::text AS outbox_id,
                    outbox.topic,
                    outbox.partition_key,
                    outbox.deduplication_key,
                    outbox.status,
                    outbox.payload::text AS payload,
                    (
                        (
                            outbox.payload
                                ->> 'occurredAt'
                        )::timestamptz
                        = payment.completed_at
                    ) AS occurred_at_matches
                FROM outbox_events outbox
                JOIN payment_transactions payment
                  ON payment.id = outbox.aggregate_id
                WHERE outbox.aggregate_id = ?
                """,
                (resultSet, rowNumber) ->
                    new OutboxMetadata(
                        resultSet.getString(
                            "outbox_id"
                        ),
                        resultSet.getString(
                            "topic"
                        ),
                        resultSet.getString(
                            "partition_key"
                        ),
                        resultSet.getString(
                            "deduplication_key"
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getString(
                            "payload"
                        ),
                        resultSet.getBoolean(
                            "occurred_at_matches"
                        )
                    ),
                transactionId
            );

        assertThat(metadata)
            .isNotNull();

        assertThat(metadata.topic())
            .isEqualTo(
                "wallet.transfer.completed"
            );

        assertThat(metadata.partitionKey())
            .isEqualTo(
                transactionId.toString()
            );

        assertThat(metadata.deduplicationKey())
            .isEqualTo(
                "wallet.transfer.completed:1:"
                    + transactionId
            );

        assertThat(metadata.status())
            .isEqualTo("PENDING");

        assertThat(metadata.occurredAtMatches())
            .isTrue();

        JsonNode payload =
            objectMapper.readTree(
                metadata.payload()
            );

        assertThat(payload.size())
            .isEqualTo(9);

        assertThat(
            payload.get("eventId").asText()
        ).isEqualTo(
            metadata.outboxId()
        );

        assertThat(
            payload.get("eventType").asText()
        ).isEqualTo(
            "wallet.transfer.completed"
        );

        assertThat(
            payload.get("eventVersion").asInt()
        ).isEqualTo(1);

        assertThat(
            payload.get("transactionId").asText()
        ).isEqualTo(
            transactionId.toString()
        );

        assertThat(
            payload.get("sourceWalletId").asText()
        ).isEqualTo(
            sourceWalletId.toString()
        );

        assertThat(
            payload.get("targetWalletId").asText()
        ).isEqualTo(
            targetWalletId.toString()
        );

        assertThat(
            payload.get("amount").isTextual()
        ).isTrue();

        assertThat(
            payload.get("amount").asText()
        ).isEqualTo("125.50");

        assertThat(
            payload.get("currency").asText()
        ).isEqualTo("TRY");

        assertThat(
            payload.has("idempotencyKey")
        ).isFalse();
    }

    private record OutboxMetadata(
        String outboxId,
        String topic,
        String partitionKey,
        String deduplicationKey,
        String status,
        String payload,
        boolean occurredAtMatches
    ) {
    }
}
