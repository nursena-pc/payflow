package com.nursena.payflow.wallet.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.Duration;
import java.math.BigDecimal;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WalletManagementIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateTopUpRetrieveAndRejectSecondWallet()
        throws Exception {

        String email = uniqueEmail("wallet");
        String password = "StrongPassword123!";

        registerUser(email, password);

        String accessToken =
            authenticateUser(email, password);

        MvcResult walletCreationResult =
            openWallet(accessToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.ownerId").isNotEmpty())
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        JsonNode createdWallet = objectMapper.readTree(
            walletCreationResult
                .getResponse()
                .getContentAsString()
        );

        topUpWallet(
            accessToken,
            new BigDecimal("250.00")
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.id")
                    .value(createdWallet.get("id").asText())
            )
            .andExpect(
                jsonPath("$.balance")
                    .value(250.00)
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
                    .isNotEmpty()
            )
            .andExpect(
                jsonPath("$.ownerId")
                    .doesNotExist()
            );

        MvcResult currentWalletResult =
            getCurrentWallet(accessToken)
                .andExpect(status().isOk())
                .andExpect(
                    jsonPath("$.id")
                        .value(createdWallet.get("id").asText())
                )
                .andExpect(
                    jsonPath("$.balance")
                        .value(250.00)
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
                        .isNotEmpty()
                )
                .andExpect(
                    jsonPath("$.ownerId")
                        .doesNotExist()
                )
                .andReturn();

        JsonNode retrievedWallet = objectMapper.readTree(
            currentWalletResult
                .getResponse()
                .getContentAsString()
        );

        Instant createdAtFromCreation = Instant.parse(
            createdWallet
                .get("createdAt")
                .asText()
        );

        Instant createdAtFromRetrieval = Instant.parse(
            retrievedWallet
                .get("createdAt")
                .asText()
        );

        Duration timestampDifference = Duration
            .between(
                createdAtFromCreation,
                createdAtFromRetrieval
            )
            .abs();

        assertThat(timestampDifference)
            .isLessThanOrEqualTo(
                Duration.ofNanos(1_000)
            );

        openWallet(accessToken)
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.status")
                    .value(409)
            )
            .andExpect(
                jsonPath("$.code")
                    .value("WALLET_ALREADY_EXISTS")
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "User already has a wallet."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value("/api/v1/wallets")
            )
            .andExpect(
                jsonPath("$.violations")
                    .isEmpty()
            );
    }

    @Test
    void shouldReturnNotFoundWhenCurrentUserHasNoWallet()
        throws Exception {

        String email = uniqueEmail("wallet-not-found");
        String password = "StrongPassword123!";

        registerUser(email, password);

        String accessToken =
            authenticateUser(email, password);

        getCurrentWallet(accessToken)
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
    }

    @Test
    void shouldReturnNotFoundWhenTopUpUserHasNoWallet()
        throws Exception {

        String email = uniqueEmail("top-up-not-found");
        String password = "StrongPassword123!";

        registerUser(email, password);

        String accessToken =
            authenticateUser(email, password);

        topUpWallet(
            accessToken,
            new BigDecimal("50.00")
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
            )
            .andExpect(
                jsonPath("$.violations")
                    .isEmpty()
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
            .andExpect(
                jsonPath("$.accessToken")
                    .isNotEmpty()
            )
            .andExpect(
                jsonPath("$.tokenType")
                    .value("Bearer")
            )
            .andReturn();

        JsonNode responseBody = objectMapper.readTree(
            result.getResponse().getContentAsString()
        );

        return responseBody
            .get("accessToken")
            .asText();
    }

    private ResultActions openWallet(
        String accessToken
    ) throws Exception {

        return mockMvc.perform(
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
        );
    }

    private ResultActions topUpWallet(
        String accessToken,
        BigDecimal amount
    ) throws Exception {

        return mockMvc.perform(
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
        );
    }

    private ResultActions getCurrentWallet(
        String accessToken
    ) throws Exception {

        return mockMvc.perform(
            get("/api/v1/wallets/me")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    bearer(accessToken)
                )
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
