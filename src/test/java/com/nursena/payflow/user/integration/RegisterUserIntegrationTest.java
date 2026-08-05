package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;

import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RegisterUserIntegrationTest {

    private static final String RAW_PASSWORD =
        "StrongPassword123!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRESQL =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldRegisterUserThroughCompleteApplicationFlow() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "email": "Integration.User@Example.COM",
                                  "password": "StrongPassword123!"
                                }
                                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").isNotEmpty());

        UserDatabaseRow user = jdbcTemplate.queryForObject(
            """
            SELECT
                email,
                password_hash,
                role,
                status,
                email_verified_at
            FROM users
            WHERE email = ?
            """,
            (resultSet, rowNumber) -> new UserDatabaseRow(
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getString("role"),
                resultSet.getString("status"),
                resultSet.getTimestamp("email_verified_at")
            ),
            "integration.user@example.com"
        );

        assertThat(user).isNotNull();
        assertThat(user.email())
            .isEqualTo("integration.user@example.com");
        assertThat(user.passwordHash())
            .isNotEqualTo(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(
            RAW_PASSWORD,
            user.passwordHash()
        )).isTrue();
        assertThat(user.role())
            .isEqualTo(UserRole.USER.name());
        assertThat(user.status())
            .isEqualTo(UserStatus.ACTIVE.name());
        assertThat(user.emailVerifiedAt()).isNull();
    }

    @Test
    void shouldRejectDuplicateEmailAndKeepSingleDatabaseRecord()
        throws Exception {

        String requestBody = """
                {
                  "email": "duplicate@example.com",
                  "password": "StrongPassword123!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value("EMAIL_ALREADY_REGISTERED"))
            .andExpect(jsonPath("$.message")
                .value(
                    "A user with this email address already exists."
                ));

        Integer userCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM users
            WHERE email = ?
            """,
            Integer.class,
            "duplicate@example.com"
        );

        assertThat(userCount).isEqualTo(1);
    }

    private record UserDatabaseRow(
        String email,
        String passwordHash,
        String role,
        String status,
        Timestamp emailVerifiedAt
    ) {
    }
}
