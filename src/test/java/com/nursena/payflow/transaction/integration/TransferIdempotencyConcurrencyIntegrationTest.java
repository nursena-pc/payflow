package com.nursena.payflow.transaction.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyCommand;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyResult;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyUseCase;
import com.nursena.payflow.transaction.application.port.out.PaymentTransactionRepositoryPort;
import com.nursena.payflow.transaction.domain.exception.IdempotencyConflictException;
import com.nursena.payflow.transaction.domain.model.IdempotencyKey;
import com.nursena.payflow.transaction.domain.model.PaymentTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Import(
    TransferIdempotencyConcurrencyIntegrationTest
        .RepositoryBarrierConfiguration.class
)
class TransferIdempotencyConcurrencyIntegrationTest {

    private static final BigDecimal INITIAL_SOURCE_BALANCE =
        new BigDecimal("300.00");

    private static final BigDecimal TRANSFER_AMOUNT =
        new BigDecimal("125.50");

    private static final BigDecimal EXPECTED_SOURCE_BALANCE =
        new BigDecimal("174.50");

    private static final BigDecimal EXPECTED_TARGET_BALANCE =
        new BigDecimal("125.50");

    private static final String IDEMPOTENCY_KEY =
        "concurrent-transfer-request-1";

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

    @Autowired
    private CoordinatedPaymentTransactionRepository
        transactionRepositoryBarrier;

    @Test
    void shouldPersistOnlyOneTransferForConcurrentDuplicateRequests()
        throws Exception {

        String password = "StrongPassword123!";

        String sourceEmail =
            uniqueEmail("concurrent-source");

        String targetEmail =
            uniqueEmail("concurrent-target");

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

        transactionRepositoryBarrier
            .coordinateNextLookups(2);

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        try {
            Future<TransferAttempt> firstFuture =
                executor.submit(() ->
                    executeTransfer(command)
                );

            Future<TransferAttempt> secondFuture =
                executor.submit(() ->
                    executeTransfer(command)
                );

            boolean bothRequestsPassedInitialLookup =
                transactionRepositoryBarrier
                    .awaitCoordinatedLookups(
                        10,
                        SECONDS
                    );

            assertThat(
                bothRequestsPassedInitialLookup
            )
                .as(
                    "Both requests should observe no existing "
                        + "transaction before either insert proceeds"
                )
                .isTrue();

            transactionRepositoryBarrier
                .releaseCoordinatedLookups();

            TransferAttempt firstAttempt =
                firstFuture.get(30, SECONDS);

            TransferAttempt secondAttempt =
                secondFuture.get(30, SECONDS);

            List<TransferAttempt> attempts =
                List.of(
                    firstAttempt,
                    secondAttempt
                );

            List<TransferAttempt> successfulAttempts =
                attempts.stream()
                    .filter(TransferAttempt::isSuccessful)
                    .toList();

            List<TransferAttempt> failedAttempts =
                attempts.stream()
                    .filter(attempt ->
                        !attempt.isSuccessful()
                    )
                    .toList();

            assertThat(successfulAttempts)
                .hasSize(1);

            assertThat(failedAttempts)
                .hasSize(1);

            TransferMoneyResult successfulResult =
                successfulAttempts
                    .getFirst()
                    .result();

            assertThat(successfulResult.transactionId())
                .isNotNull();

            assertThat(successfulResult.amount())
                .isEqualByComparingTo(
                    TRANSFER_AMOUNT
                );

            assertThat(
                failedAttempts
                    .getFirst()
                    .failure()
            )
                .isInstanceOf(
                    IdempotencyConflictException.class
                )
                .hasMessage(
                    "Idempotency key has already been used "
                        + "for another transfer request."
                );

            assertPersistedState(
                sourceWallet.id(),
                targetWallet.id(),
                successfulResult.transactionId()
            );
        } finally {
            transactionRepositoryBarrier
                .releaseCoordinatedLookups();

            executor.shutdownNow();

            boolean terminated =
                executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
                );

            assertThat(terminated)
                .as(
                    "Concurrent transfer executor "
                        + "should terminate"
                )
                .isTrue();
        }
    }

    private TransferAttempt executeTransfer(
        TransferMoneyCommand command
    ) {
        try {
            return TransferAttempt.success(
                transferMoneyUseCase.transfer(command)
            );
        } catch (Throwable exception) {
            return TransferAttempt.failure(exception);
        }
    }

    private void assertPersistedState(
        UUID sourceWalletId,
        UUID targetWalletId,
        UUID transactionId
    ) {
        assertThat(walletBalance(sourceWalletId))
            .isEqualByComparingTo(
                EXPECTED_SOURCE_BALANCE
            );

        assertThat(walletBalance(targetWalletId))
            .isEqualByComparingTo(
                EXPECTED_TARGET_BALANCE
            );

        assertThat(walletVersion(sourceWalletId))
            .isEqualTo(2L);

        assertThat(walletVersion(targetWalletId))
            .isEqualTo(1L);

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

        Long debitCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ledger_entries
                WHERE transaction_id = ?
                  AND wallet_id = ?
                  AND entry_type = 'DEBIT'
                  AND amount = ?
                  AND currency = 'TRY'
                """,
                Long.class,
                transactionId,
                sourceWalletId,
                TRANSFER_AMOUNT
            );

        assertThat(debitCount)
            .isEqualTo(1L);

        Long creditCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ledger_entries
                WHERE transaction_id = ?
                  AND wallet_id = ?
                  AND entry_type = 'CREDIT'
                  AND amount = ?
                  AND currency = 'TRY'
                """,
                Long.class,
                transactionId,
                targetWalletId,
                TRANSFER_AMOUNT
            );

        Long outboxEventCount =
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

        assertThat(outboxEventCount)
            .isEqualTo(1L);

        String outboxStatus =
            jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM outbox_events
                WHERE aggregate_id = ?
                """,
                String.class,
                transactionId
            );

        assertThat(outboxStatus)
            .isEqualTo("PENDING");

        String partitionKey =
            jdbcTemplate.queryForObject(
                """
                SELECT partition_key
                FROM outbox_events
                WHERE aggregate_id = ?
                """,
                String.class,
                transactionId
            );

        assertThat(partitionKey)
            .isEqualTo(
                transactionId.toString()
            );

        String deduplicationKey =
            jdbcTemplate.queryForObject(
                """
                SELECT deduplication_key
                FROM outbox_events
                WHERE aggregate_id = ?
                """,
                String.class,
                transactionId
            );

        assertThat(deduplicationKey)
            .isEqualTo(
                "wallet.transfer.completed:1:"
                    + transactionId
            );

        String payloadTransactionId =
            jdbcTemplate.queryForObject(
                """
                SELECT payload ->> 'transactionId'
                FROM outbox_events
                WHERE aggregate_id = ?
                """,
                String.class,
                transactionId
            );

        assertThat(payloadTransactionId)
            .isEqualTo(
                transactionId.toString()
            );

        assertThat(creditCount)
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

    private Long walletVersion(
        UUID walletId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT version
            FROM wallets
            WHERE id = ?
            """,
            Long.class,
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

    private record TransferAttempt(
        TransferMoneyResult result,
        Throwable failure
    ) {

        static TransferAttempt success(
            TransferMoneyResult result
        ) {
            return new TransferAttempt(
                result,
                null
            );
        }

        static TransferAttempt failure(
            Throwable failure
        ) {
            return new TransferAttempt(
                null,
                failure
            );
        }

        boolean isSuccessful() {
            return result != null;
        }
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

    @TestConfiguration(proxyBeanMethods = false)
    static class RepositoryBarrierConfiguration {

        @Bean
        @Primary
        CoordinatedPaymentTransactionRepository
        coordinatedPaymentTransactionRepository(
            @Qualifier(
                "paymentTransactionPersistenceAdapter"
            )
            PaymentTransactionRepositoryPort delegate
        ) {

            return new CoordinatedPaymentTransactionRepository(
                delegate
            );
        }
    }

    static final class
    CoordinatedPaymentTransactionRepository
        implements PaymentTransactionRepositoryPort {

        private final PaymentTransactionRepositoryPort delegate;

        private volatile CountDownLatch observedLookups =
            new CountDownLatch(0);

        private volatile CountDownLatch releaseLookups =
            new CountDownLatch(0);

        CoordinatedPaymentTransactionRepository(
            PaymentTransactionRepositoryPort delegate
        ) {
            this.delegate = delegate;
        }

        void coordinateNextLookups(int participants) {
            observedLookups =
                new CountDownLatch(participants);

            releaseLookups =
                new CountDownLatch(1);
        }

        boolean awaitCoordinatedLookups(
            long timeout,
            TimeUnit unit
        ) throws InterruptedException {

            return observedLookups.await(
                timeout,
                unit
            );
        }

        void releaseCoordinatedLookups() {
            releaseLookups.countDown();
        }

        @Override
        public Optional<PaymentTransaction>
        findBySourceWalletIdAndIdempotencyKey(
            UUID sourceWalletId,
            IdempotencyKey idempotencyKey
        ) {

            Optional<PaymentTransaction> result =
                delegate
                    .findBySourceWalletIdAndIdempotencyKey(
                        sourceWalletId,
                        idempotencyKey
                    );

            CountDownLatch observed =
                observedLookups;

            CountDownLatch release =
                releaseLookups;

            observed.countDown();
            awaitRelease(release);

            return result;
        }

        @Override
        public PaymentTransaction save(
            PaymentTransaction transaction
        ) {
            return delegate.save(transaction);
        }

        @Override
        public PaymentTransaction update(
            PaymentTransaction transaction
        ) {
            return delegate.update(transaction);
        }

        private static void awaitRelease(
            CountDownLatch release
        ) {
            try {
                boolean released =
                    release.await(
                        10,
                        SECONDS
                    );

                if (!released) {
                    throw new IllegalStateException(
                        "Idempotency lookup barrier timed out."
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                throw new IllegalStateException(
                    "Idempotency lookup barrier "
                        + "was interrupted.",
                    exception
                );
            }
        }
    }
}
