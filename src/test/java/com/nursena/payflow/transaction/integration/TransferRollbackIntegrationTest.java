package com.nursena.payflow.transaction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.ledger.application.port.out.LedgerRepositoryPort;
import com.nursena.payflow.ledger.domain.model.DoubleEntryLedger;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyCommand;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransferRollbackIntegrationTest {

    private static final BigDecimal INITIAL_SOURCE_BALANCE =
        new BigDecimal("300.00");

    private static final BigDecimal TRANSFER_AMOUNT =
        new BigDecimal("125.50");

    private static final String IDEMPOTENCY_KEY =
        "transfer-rollback-request-1";

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

    @MockitoBean
    private LedgerRepositoryPort ledgerRepository;

    @Test
    void shouldRollbackEntireTransferWhenLedgerPersistenceFails()
        throws Exception {

        String password = "StrongPassword123!";

        String sourceEmail =
            uniqueEmail("rollback-source");

        String targetEmail =
            uniqueEmail("rollback-target");

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

        assertThat(walletBalance(sourceWallet.id()))
            .isEqualByComparingTo(
                INITIAL_SOURCE_BALANCE
            );

        assertThat(walletBalance(targetWallet.id()))
            .isEqualByComparingTo(
                BigDecimal.ZERO
            );

        assertThat(walletVersion(sourceWallet.id()))
            .isEqualTo(1L);

        assertThat(walletVersion(targetWallet.id()))
            .isEqualTo(0L);

        when(ledgerRepository.save(
            any(DoubleEntryLedger.class)
        )).thenThrow(
            new IllegalStateException(
                "Simulated ledger persistence failure."
            )
        );

        TransferMoneyCommand command =
            new TransferMoneyCommand(
                sourceWallet.ownerId(),
                targetWallet.id(),
                TRANSFER_AMOUNT,
                IDEMPOTENCY_KEY
            );

        assertThatThrownBy(() ->
            transferMoneyUseCase.transfer(command)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Simulated ledger persistence failure."
            );

        assertThat(walletBalance(sourceWallet.id()))
            .isEqualByComparingTo(
                INITIAL_SOURCE_BALANCE
            );

        assertThat(walletBalance(targetWallet.id()))
            .isEqualByComparingTo(
                BigDecimal.ZERO
            );

        assertThat(walletVersion(sourceWallet.id()))
            .isEqualTo(1L);

        assertThat(walletVersion(targetWallet.id()))
            .isEqualTo(0L);

        Long transactionCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM payment_transactions
                WHERE source_wallet_id = ?
                  AND idempotency_key = ?
                """,
                Long.class,
                sourceWallet.id(),
                IDEMPOTENCY_KEY
            );

        assertThat(transactionCount)
            .isZero();

        Long ledgerEntryCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ledger_entries
                WHERE wallet_id IN (?, ?)
                """,
                Long.class,
                sourceWallet.id(),
                targetWallet.id()
            );

        assertThat(ledgerEntryCount)
            .isZero();
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
}
