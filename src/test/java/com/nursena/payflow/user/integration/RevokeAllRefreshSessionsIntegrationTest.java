package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RevokeAllRefreshSessionsIntegrationTest {

    private static final String PASSWORD =
        "StrongPassword123!";

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
    void shouldRevokeOnlyActiveFamiliesOwnedBySubject()
        throws Exception {

        UUID userId =
            registerUser("all-session-owner");

        UUID otherUserId =
            registerUser("all-session-other");

        Instant now =
            Instant.now()
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        UUID firstActiveId =
            insertFamily(
                userId,
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                null,
                null
            );

        UUID secondActiveId =
            insertFamily(
                userId,
                now.minusSeconds(1800),
                now.plusSeconds(172800),
                null,
                null
            );

        UUID expiredId =
            insertFamily(
                userId,
                now.minusSeconds(7200),
                now.minusSeconds(1),
                null,
                null
            );

        UUID futureId =
            insertFamily(
                userId,
                now.plusSeconds(3600),
                now.plusSeconds(86400),
                null,
                null
            );

        Instant earlierRevokedAt =
            now.minusSeconds(600);

        UUID previouslyRevokedId =
            insertFamily(
                userId,
                now.minusSeconds(7200),
                now.plusSeconds(86400),
                earlierRevokedAt,
                "CURRENT_SESSION_LOGOUT"
            );

        UUID otherUserActiveId =
            insertFamily(
                otherUserId,
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                null,
                null
            );

        performLogoutAll(userId);

        assertRevokedWithAllSessionsReason(
            firstActiveId
        );

        assertRevokedWithAllSessionsReason(
            secondActiveId
        );

        assertUnrevoked(expiredId);
        assertUnrevoked(futureId);
        assertUnrevoked(otherUserActiveId);

        FamilyState previouslyRevoked =
            familyState(previouslyRevokedId);

        assertThat(
            previouslyRevoked.revokedAt()
        )
            .isEqualTo(
                earlierRevokedAt
            );

        assertThat(
            previouslyRevoked.reason()
        )
            .isEqualTo(
                "CURRENT_SESSION_LOGOUT"
            );
    }

    @Test
    void shouldRemainIdempotentAndPreserveFirstMutation()
        throws Exception {

        UUID userId =
            registerUser("all-session-idempotent");

        Instant now =
            Instant.now()
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        UUID familyId =
            insertFamily(
                userId,
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                null,
                null
            );

        performLogoutAll(userId);

        FamilyState first =
            familyState(familyId);

        performLogoutAll(userId);

        FamilyState second =
            familyState(familyId);

        assertThat(first.revokedAt())
            .isNotNull();

        assertThat(second.revokedAt())
            .isEqualTo(
                first.revokedAt()
            );

        assertThat(second.reason())
            .isEqualTo(
                "ALL_SESSIONS_LOGOUT"
            );
    }

    @Test
    void shouldReturnNoContentWhenNoFamilyExists()
        throws Exception {

        UUID userId =
            registerUser("all-session-empty");

        performLogoutAll(userId);

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

        assertThat(familyCount)
            .isZero();
    }

    private void performLogoutAll(
        UUID userId
    ) throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/auth/logout-all"
                )
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                userId.toString()
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

    private UUID registerUser(
        String prefix
    ) throws Exception {

        String email =
            prefix
                + "-"
                + UUID.randomUUID()
                + "@example.com";

        mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            new RegistrationRequest(
                                email,
                                PASSWORD
                            )
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            );

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

        return userId;
    }

    private UUID insertFamily(
        UUID userId,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        String reason
    ) {
        UUID familyId =
            UUID.randomUUID();

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO refresh_token_families (
                    id,
                    user_id,
                    created_at,
                    expires_at,
                    revoked_at,
                    revocation_reason
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                familyId,
                userId,
                Timestamp.from(createdAt),
                Timestamp.from(expiresAt),
                revokedAt == null
                    ? null
                    : Timestamp.from(revokedAt),
                reason
            );

        assertThat(inserted)
            .isEqualTo(1);

        return familyId;
    }

    private void assertRevokedWithAllSessionsReason(
        UUID familyId
    ) {
        FamilyState state =
            familyState(familyId);

        assertThat(state.revokedAt())
            .isNotNull();

        assertThat(state.reason())
            .isEqualTo(
                "ALL_SESSIONS_LOGOUT"
            );
    }

    private void assertUnrevoked(
        UUID familyId
    ) {
        FamilyState state =
            familyState(familyId);

        assertThat(state.revokedAt())
            .isNull();

        assertThat(state.reason())
            .isNull();
    }

    private FamilyState familyState(
        UUID familyId
    ) {
        FamilyState state =
            jdbcTemplate.queryForObject(
                """
                SELECT
                    id,
                    revoked_at,
                    revocation_reason
                FROM refresh_token_families
                WHERE id = ?
                """,
                (resultSet, rowNumber) -> {
                    Timestamp revokedAt =
                        resultSet.getTimestamp(
                            "revoked_at"
                        );

                    return new FamilyState(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        revokedAt == null
                            ? null
                            : revokedAt.toInstant(),
                        resultSet.getString(
                            "revocation_reason"
                        )
                    );
                },
                familyId
            );

        assertThat(state)
            .isNotNull();

        return state;
    }

    private record RegistrationRequest(
        String email,
        String password
    ) {
    }

    private record FamilyState(
        UUID id,
        Instant revokedAt,
        String reason
    ) {
    }
}
