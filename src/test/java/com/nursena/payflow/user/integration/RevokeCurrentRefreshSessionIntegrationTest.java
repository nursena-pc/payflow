package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
        "payflow.security.refresh-session.refresh-token-ttl=7d",
        "payflow.security.refresh-session.family-ttl=30d"
    }
)
@AutoConfigureMockMvc
@Testcontainers
class RevokeCurrentRefreshSessionIntegrationTest {

    private static final String UNKNOWN_CANONICAL_TOKEN =
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM refresh_token_records"
        );

        jdbcTemplate.update(
            "DELETE FROM refresh_token_families"
        );

        jdbcTemplate.update(
            "DELETE FROM users"
        );
    }

    @Test
    void shouldDurablyRevokeActiveCurrentSession()
        throws Exception {

        InitialCredential initial =
            issueInitialCredential(
                "logout-active"
            );

        UUID familyId =
            findFamilyId(initial.email());

        assertLogoutNoContent(
            initial.refreshToken()
        );

        FamilyRevocation revocation =
            findFamilyRevocation(familyId);

        assertThat(revocation.revokedAt())
            .isNotNull();

        assertThat(revocation.reason())
            .isEqualTo(
                "CURRENT_SESSION_LOGOUT"
            );

        assertThat(countFamilies())
            .isEqualTo(1);

        assertThat(countRecords())
            .isEqualTo(1);

        assertRefreshTokenRejected(
            initial.refreshToken()
        );

        assertThat(
            findFamilyRevocation(familyId)
        ).isEqualTo(revocation);

        assertThat(countRecords())
            .isEqualTo(1);
    }

    @Test
    void shouldRevokeFamilyThroughConsumedPredecessor()
        throws Exception {

        InitialCredential initial =
            issueInitialCredential(
                "logout-consumed"
            );

        UUID familyId =
            findFamilyId(initial.email());

        String successorToken =
            rotate(initial.refreshToken());

        assertThat(countRecords())
            .isEqualTo(2);

        assertLogoutNoContent(
            initial.refreshToken()
        );

        FamilyRevocation revocation =
            findFamilyRevocation(familyId);

        assertThat(revocation.revokedAt())
            .isNotNull();

        assertThat(revocation.reason())
            .isEqualTo(
                "CURRENT_SESSION_LOGOUT"
            );

        assertRefreshTokenRejected(
            initial.refreshToken()
        );

        assertRefreshTokenRejected(
            successorToken
        );

        assertThat(
            findFamilyRevocation(familyId)
        ).isEqualTo(revocation);

        assertThat(countRecords())
            .isEqualTo(2);
    }

    @Test
    void shouldHideMalformedAndUnknownCredentialState()
        throws Exception {

        assertLogoutNoContent(
            "not-canonical"
        );

        assertLogoutNoContent(
            UNKNOWN_CANONICAL_TOKEN
        );

        assertThat(countFamilies())
            .isZero();

        assertThat(countRecords())
            .isZero();
    }

    @Test
    void shouldPreserveExistingReuseRevocationReason()
        throws Exception {

        InitialCredential initial =
            issueInitialCredential(
                "logout-reuse"
            );

        UUID familyId =
            findFamilyId(initial.email());

        String successorToken =
            rotate(initial.refreshToken());

        assertRefreshTokenRejected(
            initial.refreshToken()
        );

        FamilyRevocation reuseRevocation =
            findFamilyRevocation(familyId);

        assertThat(reuseRevocation.revokedAt())
            .isNotNull();

        assertThat(reuseRevocation.reason())
            .isEqualTo("REUSE_DETECTED");

        assertLogoutNoContent(
            successorToken
        );

        assertLogoutNoContent(
            successorToken
        );

        assertThat(
            findFamilyRevocation(familyId)
        ).isEqualTo(reuseRevocation);

        assertThat(countRecords())
            .isEqualTo(2);
    }

    private void assertLogoutNoContent(
        String refreshToken
    ) throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/logout")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            new RefreshRequest(
                                refreshToken
                            )
                        )
                    )
            )
            .andExpect(
                status().isNoContent()
            )
            .andExpect(
                content().string("")
            );
    }

    private String rotate(
        String refreshToken
    ) throws Exception {

        MvcResult result =
            mockMvc.perform(
                    post("/api/v1/auth/refresh")
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            objectMapper.writeValueAsString(
                                new RefreshRequest(
                                    refreshToken
                                )
                            )
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andExpect(
                    jsonPath(
                        "$.refreshToken"
                    ).isNotEmpty()
                )
                .andReturn();

        JsonNode response =
            objectMapper.readTree(
                result
                    .getResponse()
                    .getContentAsByteArray()
            );

        String successorToken =
            response
                .path("refreshToken")
                .asText();

        assertThat(successorToken)
            .hasSize(43)
            .isNotEqualTo(refreshToken);

        return successorToken;
    }

    private void assertRefreshTokenRejected(
        String refreshToken
    ) throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            new RefreshRequest(
                                refreshToken
                            )
                        )
                    )
            )
            .andExpect(
                status().isUnauthorized()
            )
            .andExpect(
                jsonPath(
                    "$.code"
                ).value(
                    "REFRESH_TOKEN_INVALID"
                )
            )
            .andExpect(
                jsonPath(
                    "$.message"
                ).value(
                    "Refresh token is invalid."
                )
            )
            .andExpect(
                jsonPath(
                    "$.accessToken"
                ).doesNotExist()
            )
            .andExpect(
                jsonPath(
                    "$.refreshToken"
                ).doesNotExist()
            );
    }

    private InitialCredential issueInitialCredential(
        String prefix
    ) throws Exception {

        String email =
            prefix
                + "-"
                + UUID.randomUUID()
                + "@example.com";

        String password =
            "StrongPassword123!";

        mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            new Credentials(
                                email,
                                password
                            )
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            );

        MvcResult loginResult =
            mockMvc.perform(
                    post("/api/v1/auth/login")
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            objectMapper.writeValueAsString(
                                new Credentials(
                                    email,
                                    password
                                )
                            )
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        JsonNode loginResponse =
            objectMapper.readTree(
                loginResult
                    .getResponse()
                    .getContentAsByteArray()
            );

        return new InitialCredential(
            email,
            loginResponse
                .path("refreshToken")
                .asText()
        );
    }

    private UUID findFamilyId(
        String email
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT family.id
            FROM refresh_token_families family
            JOIN users user_account
              ON user_account.id = family.user_id
            WHERE user_account.email = ?
            """,
            UUID.class,
            email
        );
    }

    private FamilyRevocation findFamilyRevocation(
        UUID familyId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT revoked_at, revocation_reason
            FROM refresh_token_families
            WHERE id = ?
            """,
            RevokeCurrentRefreshSessionIntegrationTest
                ::mapFamilyRevocation,
            familyId
        );
    }

    private int countFamilies() {
        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_families
                """,
                Integer.class
            );

        return count == null
            ? 0
            : count;
    }

    private int countRecords() {
        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_records
                """,
                Integer.class
            );

        return count == null
            ? 0
            : count;
    }

    private static FamilyRevocation
    mapFamilyRevocation(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {

        java.sql.Timestamp revokedAt =
            resultSet.getTimestamp(
                "revoked_at"
            );

        return new FamilyRevocation(
            revokedAt == null
                ? null
                : revokedAt.toInstant(),
            resultSet.getString(
                "revocation_reason"
            )
        );
    }

    private record Credentials(
        String email,
        String password
    ) {
    }

    private record RefreshRequest(
        String refreshToken
    ) {
    }

    private record InitialCredential(
        String email,
        String refreshToken
    ) {
    }

    private record FamilyRevocation(
        Instant revokedAt,
        String reason
    ) {
    }
}
