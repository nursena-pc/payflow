package com.nursena.payflow.wallet.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.exception.WalletConcurrentUpdateException;
import com.nursena.payflow.wallet.domain.model.Money;
import com.nursena.payflow.wallet.domain.model.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WalletOptimisticLockingIntegrationTest {

    private static final BigDecimal TOP_UP_AMOUNT =
        new BigDecimal("50.00");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private WalletRepositoryPort walletRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRejectOneOfTwoConcurrentWalletUpdates()
        throws Exception {

        String email = uniqueEmail();
        String password = "StrongPassword123!";

        registerUser(email, password);

        String accessToken =
            authenticateUser(email, password);

        openWallet(accessToken);

        UUID ownerId = UUID.fromString(
            jwtDecoder
                .decode(accessToken)
                .getSubject()
        );

        CountDownLatch walletsLoaded =
            new CountDownLatch(2);

        CountDownLatch releaseUpdates =
            new CountDownLatch(1);

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        try {
            Future<Throwable> firstResult =
                executor.submit(() ->
                    executeConcurrentTopUp(
                        ownerId,
                        walletsLoaded,
                        releaseUpdates
                    )
                );

            Future<Throwable> secondResult =
                executor.submit(() ->
                    executeConcurrentTopUp(
                        ownerId,
                        walletsLoaded,
                        releaseUpdates
                    )
                );

            boolean bothTransactionsLoadedWallet =
                walletsLoaded.await(
                    10,
                    TimeUnit.SECONDS
                );

            assertThat(bothTransactionsLoadedWallet)
                .as(
                    "Both transactions should load "
                        + "the same wallet version"
                )
                .isTrue();

            releaseUpdates.countDown();

            Throwable firstFailure =
                firstResult.get(
                    20,
                    TimeUnit.SECONDS
                );

            Throwable secondFailure =
                secondResult.get(
                    20,
                    TimeUnit.SECONDS
                );

            List<Throwable> failures = Stream
                .of(
                    firstFailure,
                    secondFailure
                )
                .filter(Objects::nonNull)
                .toList();

            assertThat(failures)
                .hasSize(1);

            assertThat(failures.getFirst())
                .isInstanceOf(
                    WalletConcurrentUpdateException.class
                )
                .hasMessage(
                    "Wallet was updated concurrently. "
                        + "Please retry the operation."
                );

            BigDecimal storedBalance =
                jdbcTemplate.queryForObject(
                    """
                    SELECT balance
                    FROM wallets
                    WHERE owner_id = ?
                    """,
                    BigDecimal.class,
                    ownerId
                );

            Long storedVersion =
                jdbcTemplate.queryForObject(
                    """
                    SELECT version
                    FROM wallets
                    WHERE owner_id = ?
                    """,
                    Long.class,
                    ownerId
                );

            assertThat(storedBalance)
                .isEqualByComparingTo(
                    TOP_UP_AMOUNT
                );

            assertThat(storedVersion)
                .isEqualTo(1L);
        } finally {
            releaseUpdates.countDown();
            executor.shutdownNow();
        }
    }

    private Throwable executeConcurrentTopUp(
        UUID ownerId,
        CountDownLatch walletsLoaded,
        CountDownLatch releaseUpdates
    ) {
        try {
            TransactionTemplate transaction =
                new TransactionTemplate(
                    transactionManager
                );

            transaction.executeWithoutResult(status -> {
                Wallet wallet = walletRepository
                    .findByOwnerId(ownerId)
                    .orElseThrow();

                walletsLoaded.countDown();

                await(releaseUpdates);

                wallet.credit(
                    new Money(
                        TOP_UP_AMOUNT,
                        wallet.balance().currency()
                    )
                );

                walletRepository.update(wallet);
            });

            return null;
        } catch (Throwable exception) {
            return exception;
        }
    }

    private static void await(
        CountDownLatch latch
    ) {
        try {
            boolean released = latch.await(
                10,
                TimeUnit.SECONDS
            );

            if (!released) {
                throw new IllegalStateException(
                    "Concurrent update barrier timed out."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                "Concurrent update was interrupted.",
                exception
            );
        }
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

    private void openWallet(
        String accessToken
    ) throws Exception {

        mockMvc.perform(
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
            .andExpect(status().isCreated());
    }

    private static String bearer(
        String accessToken
    ) {
        return "Bearer " + accessToken;
    }

    private static String uniqueEmail() {
        return "optimistic-lock-"
            + UUID.randomUUID()
            + "@example.com";
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
}
