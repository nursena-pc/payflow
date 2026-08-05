package com.nursena.payflow.maildelivery.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.maildelivery.application.port.out.MailContentProtectionPort;
import com.nursena.payflow.maildelivery.application.port.out.MailOutboxClaimPort;
import com.nursena.payflow.maildelivery.application.port.out.MailOutboxEnqueuePort;
import com.nursena.payflow.maildelivery.application.port.out.MailOutboxLifecyclePort;
import com.nursena.payflow.maildelivery.domain.model.MailContentProtectionContext;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class MailOutboxPersistenceIntegrationTest {

    private static final Instant NOW =
        Instant.parse("2026-08-05T18:00:00Z");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MailOutboxEnqueuePort enqueuePort;
    @Autowired
    private MailOutboxClaimPort claimPort;
    @Autowired
    private MailOutboxLifecyclePort lifecyclePort;
    @Autowired
    private MailContentProtectionPort contentProtection;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldClaimRetryAndEraseProtectedContentAfterSend() {
        UUID userId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        insertUser(userId);
        insertCredential(
            credentialId,
            userId,
            "EMAIL_VERIFICATION"
        );
        String plaintext =
            "https://app.payflow.local/verify-email?token=secret";
        MailContentProtectionContext protectionContext =
            new MailContentProtectionContext(
                credentialId,
                userId,
                MailOutboxPurpose.EMAIL_VERIFICATION,
                "persistence@example.com",
                "Verify your PayFlow email"
            );
        MailOutboxMessage pending = MailOutboxMessage.pending(
            credentialId,
            userId,
            MailOutboxPurpose.EMAIL_VERIFICATION,
            "persistence@example.com",
            "Verify your PayFlow email",
            contentProtection.protect(protectionContext, plaintext),
            "<account-action-" + credentialId + "@payflow.local>",
            NOW,
            NOW.plus(Duration.ofHours(1))
        );

        enqueuePort.replaceUnresolved(pending, NOW);

        byte[] storedProtectedBody = jdbcTemplate.queryForObject(
            "SELECT protected_body FROM mail_outbox_messages WHERE id = ?",
            byte[].class,
            credentialId
        );
        assertThat(new String(storedProtectedBody)).doesNotContain("secret");

        List<MailOutboxMessage> firstClaim = claimPort.claimAvailable(
            "worker-1",
            NOW,
            Duration.ofSeconds(30),
            10
        );
        assertThat(firstClaim).hasSize(1);
        assertThat(contentProtection.reveal(
            firstClaim.getFirst().protectionContext(),
            firstClaim.getFirst().protectedContent()
        )).isEqualTo(plaintext);

        lifecyclePort.scheduleRetry(
            credentialId,
            "worker-1",
            NOW.plusSeconds(1),
            NOW.plusSeconds(31),
            "MailDeliveryException"
        );

        List<MailOutboxMessage> secondClaim = claimPort.claimAvailable(
            "worker-2",
            NOW.plusSeconds(31),
            Duration.ofSeconds(30),
            10
        );
        assertThat(secondClaim).hasSize(1);
        lifecyclePort.markSent(
            credentialId,
            "worker-2",
            NOW.plusSeconds(32)
        );

        MailRow row = jdbcTemplate.queryForObject(
            """
            SELECT status, attempt_count, protected_body, sent_at
            FROM mail_outbox_messages
            WHERE id = ?
            """,
            (resultSet, rowNumber) -> new MailRow(
                resultSet.getString("status"),
                resultSet.getInt("attempt_count"),
                resultSet.getBytes("protected_body"),
                resultSet.getTimestamp("sent_at") == null
                    ? null
                    : resultSet.getTimestamp("sent_at").toInstant()
            ),
            credentialId
        );
        assertThat(row.status()).isEqualTo("SENT");
        assertThat(row.attemptCount()).isEqualTo(2);
        assertThat(row.protectedBody()).isNull();
        assertThat(row.sentAt()).isEqualTo(NOW.plusSeconds(32));
    }

    @Test
    void shouldSupersedeOlderUnresolvedMessageForSamePurpose() {
        UUID userId = UUID.randomUUID();
        UUID firstCredentialId = UUID.randomUUID();
        UUID secondCredentialId = UUID.randomUUID();
        insertUser(userId);
        insertCredential(firstCredentialId, userId, "PASSWORD_RECOVERY");

        enqueuePort.replaceUnresolved(
            message(firstCredentialId, userId),
            NOW
        );

        jdbcTemplate.update(
            """
            UPDATE account_action_credentials
            SET superseded_at = ?
            WHERE id = ?
            """,
            Timestamp.from(NOW.plusSeconds(1)),
            firstCredentialId
        );
        insertCredential(secondCredentialId, userId, "PASSWORD_RECOVERY");

        enqueuePort.replaceUnresolved(
            message(secondCredentialId, userId),
            NOW.plusSeconds(1)
        );

        List<MailState> states = jdbcTemplate.query(
            """
            SELECT id, status, protected_body, last_error
            FROM mail_outbox_messages
            ORDER BY created_at, id
            """,
            (resultSet, rowNumber) -> new MailState(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("status"),
                resultSet.getBytes("protected_body"),
                resultSet.getString("last_error")
            )
        );
        assertThat(states).hasSize(2);
        assertThat(states)
            .filteredOn(state -> state.id().equals(firstCredentialId))
            .singleElement()
            .satisfies(state -> {
                assertThat(state.status()).isEqualTo("FAILED");
                assertThat(state.protectedBody()).isNull();
                assertThat(state.lastError())
                    .isEqualTo("SupersededByNewerCredential");
            });
        assertThat(states)
            .filteredOn(state -> state.id().equals(secondCredentialId))
            .singleElement()
            .satisfies(state -> assertThat(state.status())
                .isEqualTo("PENDING"));
    }

    private MailOutboxMessage message(UUID credentialId, UUID userId) {
        MailContentProtectionContext protectionContext =
            new MailContentProtectionContext(
                credentialId,
                userId,
                MailOutboxPurpose.PASSWORD_RECOVERY,
                "persistence@example.com",
                "Reset your PayFlow password"
            );
        return MailOutboxMessage.pending(
            credentialId,
            userId,
            MailOutboxPurpose.PASSWORD_RECOVERY,
            "persistence@example.com",
            "Reset your PayFlow password",
            contentProtection.protect(
                protectionContext,
                "https://app.payflow.local/recover-password?token="
                    + credentialId
            ),
            "<account-action-" + credentialId + "@payflow.local>",
            NOW.plusSeconds(credentialId.getLeastSignificantBits() & 1),
            NOW.plus(Duration.ofMinutes(30))
        );
    }

    private void insertUser(UUID userId) {
        jdbcTemplate.update(
            """
            INSERT INTO users (
                id, email, password_hash, role, status,
                email_verified_at, created_at, updated_at
            )
            VALUES (?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
            """,
            userId,
            "persistence-" + userId + "@example.com",
            "$2a$12$hashed-password",
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
    }

    private void insertCredential(
        UUID credentialId,
        UUID userId,
        String purpose
    ) {
        byte[] digest = new byte[32];
        long bits = credentialId.getLeastSignificantBits();
        for (int index = 0; index < digest.length; index++) {
            digest[index] = (byte) (bits + index);
        }
        jdbcTemplate.update(
            """
            INSERT INTO account_action_credentials (
                id, user_id, purpose, credential_digest,
                issued_at, expires_at, consumed_at, superseded_at
            )
            VALUES (?, ?, ?, ?, ?, ?, NULL, NULL)
            """,
            credentialId,
            userId,
            purpose,
            digest,
            Timestamp.from(NOW),
            Timestamp.from(NOW.plus(Duration.ofHours(1)))
        );
    }

    private record MailRow(
        String status,
        int attemptCount,
        byte[] protectedBody,
        Instant sentAt
    ) {
    }

    private record MailState(
        UUID id,
        String status,
        byte[] protectedBody,
        String lastError
    ) {
    }
}
