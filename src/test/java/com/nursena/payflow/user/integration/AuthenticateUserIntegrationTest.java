package com.nursena.payflow.user.integration;

import static com.nursena.payflow.user.support.EmailVerificationTestSupport.markVerified;

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
class AuthenticateUserIntegrationTest {

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
    void shouldIssueAndPersistInitialRefreshSession()
        throws Exception {

        String email =
            "login-success-"
                + UUID.randomUUID()
                + "@example.com";

        String password =
            "StrongPassword123!";

        registerUser(
            email,
            password
        );

        MvcResult loginResult =
            mockMvc.perform(
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
                loginResult
                    .getResponse()
                    .getContentAsByteArray()
            );

        String refreshToken =
            response
                .path("refreshToken")
                .asText();

        Instant responseRefreshExpiresAt =
            Instant.parse(
                response
                    .path(
                        "refreshTokenExpiresAt"
                    )
                    .asText()
            );

        assertThat(refreshToken)
            .hasSize(43);

        UUID userId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM users
                WHERE email = ?
                """,
                UUID.class,
                email
            );

        assertThat(userId)
            .isNotNull();

        Integer familyCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_families
                WHERE user_id = ?
                """,
                Integer.class,
                userId
            );

        Integer recordCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_records record
                JOIN refresh_token_families family
                  ON family.id = record.family_id
                WHERE family.user_id = ?
                """,
                Integer.class,
                userId
            );

        assertThat(familyCount)
            .isEqualTo(1);

        assertThat(recordCount)
            .isEqualTo(1);

        PersistedSession persisted =
            jdbcTemplate.queryForObject(
                """
                SELECT
                    family.created_at
                        AS family_created_at,
                    family.expires_at
                        AS family_expires_at,
                    family.revoked_at
                        AS family_revoked_at,
                    record.token_digest,
                    record.issued_at
                        AS record_issued_at,
                    record.expires_at
                        AS record_expires_at,
                    record.consumed_at,
                    record.successor_id
                FROM refresh_token_families family
                JOIN refresh_token_records record
                  ON record.family_id = family.id
                WHERE family.user_id = ?
                """,
                AuthenticateUserIntegrationTest
                    ::mapPersistedSession,
                userId
            );

        assertThat(persisted)
            .isNotNull();

        assertThat(
            persisted.familyCreatedAt()
        )
            .isEqualTo(
                persisted.recordIssuedAt()
            );

        assertThat(
            Duration.between(
                persisted.familyCreatedAt(),
                persisted.familyExpiresAt()
            )
        )
            .isEqualTo(
                Duration.ofDays(30)
            );

        assertThat(
            Duration.between(
                persisted.recordIssuedAt(),
                persisted.recordExpiresAt()
            )
        )
            .isEqualTo(
                Duration.ofDays(7)
            );

        assertThat(
            responseRefreshExpiresAt
        )
            .isEqualTo(
                persisted.recordExpiresAt()
            );

        assertThat(
            persisted.tokenDigest()
        )
            .containsExactly(
                refreshTokenDigest
                    .digest(refreshToken)
                    .value()
            );

        assertThat(
            Arrays.equals(
                persisted.tokenDigest(),
                refreshToken.getBytes(UTF_8)
            )
        )
            .isFalse();

        assertThat(
            persisted.familyRevokedAt()
        )
            .isNull();

        assertThat(
            persisted.consumedAt()
        )
            .isNull();

        assertThat(
            persisted.successorId()
        )
            .isNull();
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
            .andExpect(
                status().isCreated()
            );

        markVerified(jdbcTemplate, email);
    }

    private static PersistedSession
    mapPersistedSession(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {

        return new PersistedSession(
            resultSet
                .getTimestamp(
                    "family_created_at"
                )
                .toInstant(),
            resultSet
                .getTimestamp(
                    "family_expires_at"
                )
                .toInstant(),
            nullableInstant(
                resultSet,
                "family_revoked_at"
            ),
            resultSet.getBytes(
                "token_digest"
            ),
            resultSet
                .getTimestamp(
                    "record_issued_at"
                )
                .toInstant(),
            resultSet
                .getTimestamp(
                    "record_expires_at"
                )
                .toInstant(),
            nullableInstant(
                resultSet,
                "consumed_at"
            ),
            resultSet.getObject(
                "successor_id",
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

    private record PersistedSession(
        Instant familyCreatedAt,
        Instant familyExpiresAt,
        Instant familyRevokedAt,
        byte[] tokenDigest,
        Instant recordIssuedAt,
        Instant recordExpiresAt,
        Instant consumedAt,
        UUID successorId
    ) {
    }
}
