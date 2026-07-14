package com.nursena.payflow.wallet.integration;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WalletCreationIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateWalletAndRejectSecondWallet()
        throws Exception {

        String email = "wallet-"
            + UUID.randomUUID()
            + "@example.com";

        String password = "StrongPassword123!";

        registerUser(email, password);

        String accessToken =
            authenticateUser(email, password);

        openWallet(accessToken)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.ownerId").isNotEmpty())
            .andExpect(jsonPath("$.balance").value(0.00))
            .andExpect(jsonPath("$.currency").value("TRY"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty());

        openWallet(accessToken)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
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
            .andExpect(jsonPath("$.violations").isEmpty());
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

    private org.springframework.test.web.servlet.ResultActions
    openWallet(String accessToken) throws Exception {

        return mockMvc.perform(
            post("/api/v1/wallets")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + accessToken
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
