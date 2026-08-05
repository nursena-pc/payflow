package com.nursena.payflow.maildelivery.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

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
class AccountActionMailOutboxIntegrationTest {

    private static final String EMAIL =
        "mail-outbox.integration@example.com";
    private static final String PASSWORD =
        "StrongPassword123!";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldPersistProtectedVerificationAndRecoveryMailAtomically()
        throws Exception {

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMAIL, PASSWORD)
                )
        ).andExpect(status().isCreated());

        List<MailRow> afterRegistration = mailRows();
        assertThat(afterRegistration).hasSize(1);
        MailRow registrationMail = afterRegistration.getFirst();
        assertThat(registrationMail.purpose())
            .isEqualTo("EMAIL_VERIFICATION");
        assertThat(registrationMail.status()).isEqualTo("PENDING");
        assertThat(registrationMail.protectedBody()).isNotEmpty();
        assertThat(new String(
            registrationMail.protectedBody(),
            StandardCharsets.UTF_8
        )).doesNotContain("verify-email", "token=");
        assertThat(registrationMail.id())
            .isEqualTo(registrationMail.credentialId());

        mockMvc.perform(
            post("/api/v1/auth/email-verification/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\"}")
        ).andExpect(status().isAccepted());

        List<MailRow> afterReplacement = mailRows();
        assertThat(afterReplacement).hasSize(2);
        assertThat(afterReplacement)
            .filteredOn(row -> row.status().equals("FAILED"))
            .singleElement()
            .satisfies(row -> {
                assertThat(row.protectedBody()).isNull();
                assertThat(row.lastError())
                    .isEqualTo("SupersededByNewerCredential");
            });

        jdbcTemplate.update(
            """
            UPDATE users
            SET email_verified_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE email = ?
            """,
            EMAIL
        );

        mockMvc.perform(
            post("/api/v1/auth/password-recovery/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\"}")
        ).andExpect(status().isAccepted());

        assertThat(mailRows())
            .filteredOn(row -> row.purpose().equals("PASSWORD_RECOVERY"))
            .singleElement()
            .satisfies(row -> {
                assertThat(row.status()).isEqualTo("PENDING");
                assertThat(row.protectedBody()).isNotEmpty();
            });
    }

    private List<MailRow> mailRows() {
        return jdbcTemplate.query(
            """
            SELECT
                mail.id,
                credential.id AS credential_id,
                mail.purpose,
                mail.status,
                mail.protected_body,
                mail.last_error
            FROM mail_outbox_messages mail
            JOIN account_action_credentials credential
              ON credential.id = mail.id
            ORDER BY mail.created_at, mail.id
            """,
            (resultSet, rowNumber) -> new MailRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("credential_id", UUID.class),
                resultSet.getString("purpose"),
                resultSet.getString("status"),
                resultSet.getBytes("protected_body"),
                resultSet.getString("last_error")
            )
        );
    }

    private record MailRow(
        UUID id,
        UUID credentialId,
        String purpose,
        String status,
        byte[] protectedBody,
        String lastError
    ) {
    }
}
