package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialDigestPort;
import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialGenerationPort;
import com.nursena.payflow.user.application.port.out
    .GeneratedAccountActionCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
    .AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(
    EmailVerificationIntegrationTest
        .DeterministicCredentialConfiguration.class
)
class EmailVerificationIntegrationTest {

    private static final String EMAIL =
        "verification.integration@example.com";

    private static final String PASSWORD =
        "StrongPassword123!";

    private static final String REGISTRATION_CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    private static final String REISSUED_CREDENTIAL =
        "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountActionCredentialDigestPort credentialDigest;

    @Autowired
    private DeterministicCredentialGenerator
        credentialGenerator;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM refresh_token_records"
        );
        jdbcTemplate.update(
            "DELETE FROM refresh_token_families"
        );
        jdbcTemplate.update(
            "DELETE FROM account_action_credentials"
        );
        jdbcTemplate.update("DELETE FROM users");

        credentialGenerator.reset(
            REGISTRATION_CREDENTIAL,
            REISSUED_CREDENTIAL
        );
    }

    @Test
    void shouldRequireVerificationBeforeLoginAndConsumeOnce()
        throws Exception {

        registerUser();

        CredentialRow issued =
            singleCredentialRow();

        assertThat(issued.purpose())
            .isEqualTo("EMAIL_VERIFICATION");
        assertThat(issued.digest())
            .containsExactly(
                credentialDigest
                    .digest(REGISTRATION_CREDENTIAL)
                    .value()
            );
        assertThat(issued.digest()).hasSize(32);
        assertThat(issued.consumedAt()).isNull();
        assertThat(issued.supersededAt()).isNull();

        login()
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.code")
                    .value("USER_ACCOUNT_UNAVAILABLE")
            );

        assertThat(refreshFamilyCount()).isZero();

        confirm(REGISTRATION_CREDENTIAL)
            .andExpect(status().isNoContent());

        assertThat(emailVerifiedAt()).isNotNull();
        assertThat(singleCredentialRow().consumedAt())
            .isNotNull();

        confirm(REGISTRATION_CREDENTIAL)
            .andExpect(
                status().isUnprocessableEntity()
            )
            .andExpect(
                jsonPath("$.code").value(
                    "ACCOUNT_ACTION_CREDENTIAL_INVALID"
                )
            );

        login().andExpect(status().isOk());
    }

    @Test
    void shouldRollbackCredentialConsumptionWhenOwnershipIsAlreadyVerified()
        throws Exception {

        registerUser();

        jdbcTemplate.update(
            """
            UPDATE users
            SET email_verified_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE email = ?
            """,
            EMAIL
        );

        confirm(REGISTRATION_CREDENTIAL)
            .andExpect(
                status().isUnprocessableEntity()
            )
            .andExpect(
                jsonPath("$.code").value(
                    "ACCOUNT_ACTION_CREDENTIAL_INVALID"
                )
            );

        CredentialRow row = singleCredentialRow();

        assertThat(row.consumedAt()).isNull();
        assertThat(row.supersededAt()).isNull();
    }

    @Test
    void shouldReturnGenericAcceptedResponseAndSupersedeEligibleToken()
        throws Exception {

        registerUser();

        requestVerification(EMAIL)
            .andExpect(status().isAccepted());

        List<CredentialRow> rows = credentialRows();

        assertThat(rows).hasSize(2);
        assertThat(rows)
            .filteredOn(row -> row.supersededAt() != null)
            .hasSize(1);
        CredentialRow unresolved = rows.stream()
            .filter(row ->
                row.consumedAt() == null
                    && row.supersededAt() == null
            )
            .findFirst()
            .orElseThrow();

        assertThat(unresolved.digest())
            .containsExactly(
                credentialDigest
                    .digest(REISSUED_CREDENTIAL)
                    .value()
            );

        requestVerification("unknown@example.com")
            .andExpect(status().isAccepted());

        assertThat(credentialRows()).hasSize(2);

        confirm(REISSUED_CREDENTIAL)
            .andExpect(status().isNoContent());

        requestVerification(EMAIL)
            .andExpect(status().isAccepted());

        assertThat(credentialRows()).hasSize(2);
    }

    private void registerUser() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(EMAIL, PASSWORD))
            )
            .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions
    login() throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMAIL, PASSWORD))
        );
    }

    private org.springframework.test.web.servlet.ResultActions
    requestVerification(String email) throws Exception {
        return mockMvc.perform(
            post(
                "/api/v1/auth/email-verification/requests"
            )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s"
                    }
                    """.formatted(email))
        );
    }

    private org.springframework.test.web.servlet.ResultActions
    confirm(String credential) throws Exception {
        return mockMvc.perform(
            post(
                "/api/v1/auth/email-verification/confirm"
            )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "credential": "%s"
                    }
                    """.formatted(credential))
        );
    }

    private CredentialRow singleCredentialRow() {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                purpose,
                credential_digest,
                consumed_at,
                superseded_at
            FROM account_action_credentials
            """,
            (resultSet, rowNumber) -> new CredentialRow(
                resultSet.getString("purpose"),
                resultSet.getBytes("credential_digest"),
                resultSet.getTimestamp("consumed_at"),
                resultSet.getTimestamp("superseded_at")
            )
        );
    }

    private List<CredentialRow> credentialRows() {
        return jdbcTemplate.query(
            """
            SELECT
                purpose,
                credential_digest,
                consumed_at,
                superseded_at
            FROM account_action_credentials
            ORDER BY issued_at ASC
            """,
            (resultSet, rowNumber) -> new CredentialRow(
                resultSet.getString("purpose"),
                resultSet.getBytes("credential_digest"),
                resultSet.getTimestamp("consumed_at"),
                resultSet.getTimestamp("superseded_at")
            )
        );
    }

    private Timestamp emailVerifiedAt() {
        return jdbcTemplate.queryForObject(
            """
            SELECT email_verified_at
            FROM users
            WHERE email = ?
            """,
            Timestamp.class,
            EMAIL
        );
    }

    private int refreshFamilyCount() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_token_families",
            Integer.class
        );

        return count == null ? 0 : count;
    }

    private record CredentialRow(
        String purpose,
        byte[] digest,
        Timestamp consumedAt,
        Timestamp supersededAt
    ) {

        @Override
        public String toString() {
            return "CredentialRow["
                + "purpose=" + purpose
                + ", digest=redacted"
                + ", consumedAt=" + consumedAt
                + ", supersededAt=" + supersededAt
                + "]";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DeterministicCredentialConfiguration {

        @Bean
        @Primary
        DeterministicCredentialGenerator
        deterministicCredentialGenerator() {
            return new DeterministicCredentialGenerator();
        }
    }

    static final class DeterministicCredentialGenerator
        implements AccountActionCredentialGenerationPort {

        private final Queue<String> values =
            new ConcurrentLinkedQueue<>();

        void reset(String... credentials) {
            values.clear();
            values.addAll(Arrays.asList(credentials));
        }

        @Override
        public GeneratedAccountActionCredential generate() {
            String value = values.poll();

            if (value == null) {
                throw new IllegalStateException(
                    "No deterministic credential is available."
                );
            }

            return new GeneratedAccountActionCredential(value);
        }
    }
}
