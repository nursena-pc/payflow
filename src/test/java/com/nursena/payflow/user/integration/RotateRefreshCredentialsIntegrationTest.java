package com.nursena.payflow.user.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
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
class RotateRefreshCredentialsIntegrationTest {

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

    @Autowired
    private RefreshTokenDigestPort
        refreshTokenDigest;

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
    void shouldRotateOnceAndDurablyRevokeFamilyOnReuse()
        throws Exception {

        InitialCredential initial =
            issueInitialCredential(
                "rotation-success"
            );

        UUID predecessorId =
            jdbcTemplate.queryForObject(
                """
                SELECT record.id
                FROM refresh_token_records record
                JOIN refresh_token_families family
                  ON family.id = record.family_id
                JOIN users user_account
                  ON user_account.id = family.user_id
                WHERE user_account.email = ?
                """,
                UUID.class,
                initial.email()
            );

        assertThat(predecessorId)
            .isNotNull();

        MvcResult rotationResult =
            mockMvc.perform(
                    post("/api/v1/auth/refresh")
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            objectMapper.writeValueAsString(
                                new RefreshRequest(
                                    initial.refreshToken()
                                )
                            )
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andExpect(
                    jsonPath(
                        "$.accessToken"
                    ).isNotEmpty()
                )
                .andExpect(
                    jsonPath(
                        "$.tokenType"
                    ).value("Bearer")
                )
                .andExpect(
                    jsonPath(
                        "$.expiresAt"
                    ).isNotEmpty()
                )
                .andExpect(
                    jsonPath(
                        "$.refreshToken"
                    ).isNotEmpty()
                )
                .andExpect(
                    jsonPath(
                        "$.refreshTokenExpiresAt"
                    ).isNotEmpty()
                )
                .andExpect(
                    jsonPath(
                        "$.tokenDigest"
                    ).doesNotExist()
                )
                .andExpect(
                    jsonPath(
                        "$.familyId"
                    ).doesNotExist()
                )
                .andExpect(
                    jsonPath(
                        "$.recordId"
                    ).doesNotExist()
                )
                .andReturn();

        JsonNode response =
            objectMapper.readTree(
                rotationResult
                    .getResponse()
                    .getContentAsByteArray()
            );

        String successorToken =
            response
                .path("refreshToken")
                .asText();

        Instant responseSuccessorExpiresAt =
            Instant.parse(
                response
                    .path(
                        "refreshTokenExpiresAt"
                    )
                    .asText()
            );

        assertThat(successorToken)
            .hasSize(43)
            .isNotEqualTo(
                initial.refreshToken()
            );

        RotatedSession persisted =
            jdbcTemplate.queryForObject(
                """
                SELECT
                    predecessor.id
                        AS predecessor_id,
                    predecessor.family_id
                        AS predecessor_family_id,
                    predecessor.token_digest
                        AS predecessor_digest,
                    predecessor.consumed_at
                        AS predecessor_consumed_at,
                    predecessor.successor_id
                        AS predecessor_successor_id,
                    successor.id
                        AS successor_id,
                    successor.family_id
                        AS successor_family_id,
                    successor.token_digest
                        AS successor_digest,
                    successor.issued_at
                        AS successor_issued_at,
                    successor.expires_at
                        AS successor_expires_at,
                    successor.consumed_at
                        AS successor_consumed_at,
                    successor.successor_id
                        AS successor_successor_id
                FROM refresh_token_records predecessor
                JOIN refresh_token_records successor
                  ON successor.id =
                     predecessor.successor_id
                 AND successor.family_id =
                     predecessor.family_id
                WHERE predecessor.id = ?
                """,
                RotateRefreshCredentialsIntegrationTest
                    ::mapRotatedSession,
                predecessorId
            );

        assertThat(persisted)
            .isNotNull();

        assertThat(
            persisted.predecessorId()
        )
            .isEqualTo(
                predecessorId
            );

        assertThat(
            persisted.predecessorConsumedAt()
        )
            .isNotNull()
            .isEqualTo(
                persisted.successorIssuedAt()
            );

        assertThat(
            persisted.predecessorSuccessorId()
        )
            .isEqualTo(
                persisted.successorId()
            );

        assertThat(
            persisted.predecessorFamilyId()
        )
            .isEqualTo(
                persisted.successorFamilyId()
            );

        assertThat(
            persisted.successorConsumedAt()
        )
            .isNull();

        assertThat(
            persisted.successorSuccessorId()
        )
            .isNull();

        assertThat(
            responseSuccessorExpiresAt
        )
            .isEqualTo(
                persisted.successorExpiresAt()
            );

        assertThat(
            Duration.between(
                persisted.successorIssuedAt(),
                persisted.successorExpiresAt()
            )
        )
            .isEqualTo(
                Duration.ofDays(7)
            );

        byte[] expectedPredecessorDigest =
            refreshTokenDigest
                .digest(
                    initial.refreshToken()
                )
                .value();

        byte[] expectedSuccessorDigest =
            refreshTokenDigest
                .digest(successorToken)
                .value();

        assertThat(
            persisted.predecessorDigest()
        )
            .containsExactly(
                expectedPredecessorDigest
            );

        assertThat(
            persisted.successorDigest()
        )
            .containsExactly(
                expectedSuccessorDigest
            );

        assertThat(
            Arrays.equals(
                persisted.predecessorDigest(),
                initial
                    .refreshToken()
                    .getBytes(UTF_8)
            )
        )
            .isFalse();

        assertThat(
            Arrays.equals(
                persisted.successorDigest(),
                successorToken.getBytes(UTF_8)
            )
        )
            .isFalse();

        Integer familyCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_families
                """,
                Integer.class
            );

        Integer recordCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_records
                """,
                Integer.class
            );

        assertThat(familyCount)
            .isEqualTo(1);

        assertThat(recordCount)
            .isEqualTo(2);

        assertRefreshTokenRejected(
            initial.refreshToken()
        );

        FamilyRevocation reuseRevocation =
            findFamilyRevocation(
                persisted.predecessorFamilyId()
            );

        assertThat(reuseRevocation.revokedAt())
            .isNotNull()
            .isAfterOrEqualTo(
                persisted.predecessorConsumedAt()
            );

        assertThat(reuseRevocation.reason())
            .isEqualTo("REUSE_DETECTED");

        assertThat(countRecords())
            .isEqualTo(2);

        assertRefreshTokenRejected(
            successorToken
        );

        FamilyRevocation afterSuccessorAttempt =
            findFamilyRevocation(
                persisted.predecessorFamilyId()
            );

        assertThat(afterSuccessorAttempt)
            .isEqualTo(reuseRevocation);

        assertThat(countRecords())
            .isEqualTo(2);
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

    private FamilyRevocation findFamilyRevocation(
        UUID familyId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT revoked_at, revocation_reason
            FROM refresh_token_families
            WHERE id = ?
            """,
            (resultSet, rowNumber) ->
                new FamilyRevocation(
                    nullableInstant(
                        resultSet,
                        "revoked_at"
                    ),
                    resultSet.getString(
                        "revocation_reason"
                    )
                ),
            familyId
        );
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

    private static RotatedSession
    mapRotatedSession(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {

        return new RotatedSession(
            resultSet.getObject(
                "predecessor_id",
                UUID.class
            ),
            resultSet.getObject(
                "predecessor_family_id",
                UUID.class
            ),
            resultSet.getBytes(
                "predecessor_digest"
            ),
            resultSet
                .getTimestamp(
                    "predecessor_consumed_at"
                )
                .toInstant(),
            resultSet.getObject(
                "predecessor_successor_id",
                UUID.class
            ),
            resultSet.getObject(
                "successor_id",
                UUID.class
            ),
            resultSet.getObject(
                "successor_family_id",
                UUID.class
            ),
            resultSet.getBytes(
                "successor_digest"
            ),
            resultSet
                .getTimestamp(
                    "successor_issued_at"
                )
                .toInstant(),
            resultSet
                .getTimestamp(
                    "successor_expires_at"
                )
                .toInstant(),
            nullableInstant(
                resultSet,
                "successor_consumed_at"
            ),
            resultSet.getObject(
                "successor_successor_id",
                UUID.class
            )
        );
    }

    private static Instant nullableInstant(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        java.sql.Timestamp timestamp =
            resultSet.getTimestamp(column);

        return timestamp == null
            ? null
            : timestamp.toInstant();
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

    private record RotatedSession(
        UUID predecessorId,
        UUID predecessorFamilyId,
        byte[] predecessorDigest,
        Instant predecessorConsumedAt,
        UUID predecessorSuccessorId,
        UUID successorId,
        UUID successorFamilyId,
        byte[] successorDigest,
        Instant successorIssuedAt,
        Instant successorExpiresAt,
        Instant successorConsumedAt,
        UUID successorSuccessorId
    ) {
    }
}
