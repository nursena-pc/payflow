package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.get;
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

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(
    PasswordRecoveryIntegrationTest
        .DeterministicCredentialConfiguration.class
)
class PasswordRecoveryIntegrationTest {

    private static final String EMAIL =
        "recovery.integration@example.com";

    private static final String OLD_PASSWORD =
        "StrongPassword123!";

    private static final String NEW_PASSWORD =
        "ReplacementPassword123!";

    private static final String EMAIL_CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    private static final String FIRST_RECOVERY_CREDENTIAL =
        "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8";

    private static final String SECOND_RECOVERY_CREDENTIAL =
        "QEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW1xdXl8";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountActionCredentialDigestPort
        credentialDigest;

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
            EMAIL_CREDENTIAL,
            FIRST_RECOVERY_CREDENTIAL,
            SECOND_RECOVERY_CREDENTIAL
        );
    }

    @Test
    void shouldReplacePasswordAndRevokeExistingRefreshFamilies()
        throws Exception {

        registerAndVerify();

        String preRecoveryAccessToken =
            loginAndReadAccessToken(OLD_PASSWORD);
        login(OLD_PASSWORD).andExpect(status().isOk());

        assertThat(activeRefreshFamilyCount()).isEqualTo(2);

        requestRecovery(EMAIL)
            .andExpect(status().isAccepted());

        CredentialRow recoveryCredential =
            singleRecoveryCredential();

        assertThat(recoveryCredential.digest())
            .containsExactly(
                credentialDigest
                    .digest(FIRST_RECOVERY_CREDENTIAL)
                    .value()
            );
        assertThat(recoveryCredential.consumedAt()).isNull();
        assertThat(recoveryCredential.supersededAt()).isNull();

        confirmRecovery(
            FIRST_RECOVERY_CREDENTIAL,
            NEW_PASSWORD
        )
            .andExpect(status().isNoContent());

        assertThat(singleRecoveryCredential().consumedAt())
            .isNotNull();
        assertThat(activeRefreshFamilyCount()).isZero();
        assertThat(passwordRecoveryRevocationCount())
            .isEqualTo(2);
        assertThat(passwordEncoder.matches(
            NEW_PASSWORD,
            storedPasswordHash()
        )).isTrue();
        assertThat(passwordEncoder.matches(
            OLD_PASSWORD,
            storedPasswordHash()
        )).isFalse();

        login(OLD_PASSWORD)
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code")
                    .value("INVALID_CREDENTIALS")
            );

        mockMvc.perform(
                get("/api/v1/users/me")
                    .header(
                        AUTHORIZATION,
                        "Bearer " + preRecoveryAccessToken
                    )
            )
            .andExpect(status().isOk());

        login(NEW_PASSWORD).andExpect(status().isOk());
        assertThat(activeRefreshFamilyCount()).isEqualTo(1);
    }

    @Test
    void shouldSupersedePriorRecoveryCredentialAndKeepErrorsStable()
        throws Exception {

        registerAndVerify();

        requestRecovery(EMAIL)
            .andExpect(status().isAccepted());
        requestRecovery(EMAIL)
            .andExpect(status().isAccepted());
        requestRecovery("unknown@example.com")
            .andExpect(status().isAccepted());

        List<CredentialRow> rows = recoveryCredentials();

        assertThat(rows).hasSize(2);
        assertThat(rows)
            .filteredOn(row -> row.supersededAt() != null)
            .hasSize(1);

        confirmRecovery(
            FIRST_RECOVERY_CREDENTIAL,
            NEW_PASSWORD
        )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(
                jsonPath("$.code").value(
                    "ACCOUNT_ACTION_CREDENTIAL_INVALID"
                )
            );

        confirmRecovery(
            SECOND_RECOVERY_CREDENTIAL,
            NEW_PASSWORD
        )
            .andExpect(status().isNoContent());

        confirmRecovery(
            SECOND_RECOVERY_CREDENTIAL,
            "AnotherReplacement123!"
        )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(
                jsonPath("$.code").value(
                    "ACCOUNT_ACTION_CREDENTIAL_INVALID"
                )
            );
    }

    @Test
    void shouldReturnGenericAcceptedResponseForUnverifiedAccount()
        throws Exception {

        register();

        requestRecovery(EMAIL)
            .andExpect(status().isAccepted());

        assertThat(recoveryCredentials()).isEmpty();
    }

    private void registerAndVerify() throws Exception {
        register();

        mockMvc.perform(
                post(
                    "/api/v1/auth/email-verification/confirm"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "credential": "%s"
                        }
                        """.formatted(EMAIL_CREDENTIAL)
                    )
            )
            .andExpect(status().isNoContent());
    }

    private void register() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(EMAIL, OLD_PASSWORD)
                    )
            )
            .andExpect(status().isCreated());
    }

    private String loginAndReadAccessToken(
        String password
    ) throws Exception {
        String responseBody = login(password)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String accessToken = objectMapper
            .readTree(responseBody)
            .path("accessToken")
            .asText();

        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private org.springframework.test.web.servlet.ResultActions
    login(String password) throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMAIL, password)
                )
        );
    }

    private org.springframework.test.web.servlet.ResultActions
    requestRecovery(String email) throws Exception {
        return mockMvc.perform(
            post(
                "/api/v1/auth/password-recovery/requests"
            )
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s"
                    }
                    """.formatted(email)
                )
        );
    }

    private org.springframework.test.web.servlet.ResultActions
    confirmRecovery(
        String credential,
        String newPassword
    ) throws Exception {
        return mockMvc.perform(
            post(
                "/api/v1/auth/password-recovery/confirm"
            )
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "credential": "%s",
                      "newPassword": "%s"
                    }
                    """.formatted(
                        credential,
                        newPassword
                    )
                )
        );
    }

    private CredentialRow singleRecoveryCredential() {
        return recoveryCredentials().stream()
            .findFirst()
            .orElseThrow();
    }

    private List<CredentialRow> recoveryCredentials() {
        return jdbcTemplate.query(
            """
            SELECT
                credential_digest,
                consumed_at,
                superseded_at
            FROM account_action_credentials
            WHERE purpose = 'PASSWORD_RECOVERY'
            ORDER BY issued_at ASC
            """,
            (resultSet, rowNumber) -> new CredentialRow(
                resultSet.getBytes("credential_digest"),
                resultSet.getTimestamp("consumed_at"),
                resultSet.getTimestamp("superseded_at")
            )
        );
    }

    private String storedPasswordHash() {
        return jdbcTemplate.queryForObject(
            """
            SELECT password_hash
            FROM users
            WHERE email = ?
            """,
            String.class,
            EMAIL
        );
    }

    private int activeRefreshFamilyCount() {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM refresh_token_families
            WHERE revoked_at IS NULL
              AND revocation_reason IS NULL
            """,
            Integer.class
        );

        return count == null ? 0 : count;
    }

    private int passwordRecoveryRevocationCount() {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM refresh_token_families
            WHERE revoked_at IS NOT NULL
              AND revocation_reason = 'PASSWORD_RECOVERY'
            """,
            Integer.class
        );

        return count == null ? 0 : count;
    }

    private record CredentialRow(
        byte[] digest,
        Timestamp consumedAt,
        Timestamp supersededAt
    ) {

        @Override
        public String toString() {
            return "CredentialRow["
                + "digest=redacted"
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
