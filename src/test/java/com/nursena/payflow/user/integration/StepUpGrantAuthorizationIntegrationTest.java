package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.nursena.payflow.user.application.port.in.StepUpAuthorizationPolicy;
import com.nursena.payflow.user.application.port.out.StepUpGrantDigestPort;
import com.nursena.payflow.user.domain.exception.InvalidStepUpGrantException;
import com.nursena.payflow.user.domain.model.StepUpPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "payflow.security.mfa.step-up.ttl=5m")
@Testcontainers
class StepUpGrantAuthorizationIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired StepUpGrantDigestPort digestPort;
    @Autowired StepUpAuthorizationPolicy policy;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM step_up_grants");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldConsumeValidGrantExactlyOnce() {
        UUID subject = insertUser();
        String token = "grant-token-one";
        insertGrant(subject, StepUpPurpose.MFA_DISABLE, token, Instant.now().plusSeconds(300), null);

        policy.requireAndConsume(subject, StepUpPurpose.MFA_DISABLE, token);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT consumed_at IS NOT NULL FROM step_up_grants WHERE subject_id=?",
            Boolean.class,
            subject
        )).isTrue();
        assertThatThrownBy(() -> policy.requireAndConsume(subject, StepUpPurpose.MFA_DISABLE, token))
            .isInstanceOf(InvalidStepUpGrantException.class);
    }

    @Test
    void shouldRejectWrongSubjectPurposeExpiredAndSupersededWithoutConsuming() {
        UUID subject = insertUser();
        String token = "grant-token-two";
        insertGrant(subject, StepUpPurpose.MFA_DISABLE, token, Instant.now().plusSeconds(300), null);

        assertThatThrownBy(() -> policy.requireAndConsume(UUID.randomUUID(), StepUpPurpose.MFA_DISABLE, token))
            .isInstanceOf(InvalidStepUpGrantException.class);
        assertThatThrownBy(() -> policy.requireAndConsume(subject, StepUpPurpose.RECOVERY_CODE_ROTATION, token))
            .isInstanceOf(InvalidStepUpGrantException.class);

        Instant expiredNow = Instant.now();
        jdbcTemplate.update(
            "UPDATE step_up_grants SET issued_at=?, expires_at=? WHERE subject_id=?",
            Timestamp.from(expiredNow.minusSeconds(600)),
            Timestamp.from(expiredNow.minusSeconds(300)),
            subject
        );
        assertThatThrownBy(() -> policy.requireAndConsume(subject, StepUpPurpose.MFA_DISABLE, token))
            .isInstanceOf(InvalidStepUpGrantException.class);

        jdbcTemplate.update("UPDATE step_up_grants SET expires_at=?, superseded_at=? WHERE subject_id=?", Timestamp.from(Instant.now().plusSeconds(300)), Timestamp.from(Instant.now()), subject);
        assertThatThrownBy(() -> policy.requireAndConsume(subject, StepUpPurpose.MFA_DISABLE, token))
            .isInstanceOf(InvalidStepUpGrantException.class);
    }

    @Test
    void shouldPermitExactlyOneConcurrentGrantConsumption() throws Exception {
        UUID subject = insertUser();
        String token = "grant-token-three";
        insertGrant(subject, StepUpPurpose.MFA_DISABLE, token, Instant.now().plusSeconds(300), null);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = executor.submit(() -> consume(start, subject, token));
            Future<Boolean> second = executor.submit(() -> consume(start, subject, token));
            start.countDown();
            assertThat(List.of(first.get(), second.get()))
                .containsExactlyInAnyOrder(true, false);
        }

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM step_up_grants WHERE subject_id=? AND consumed_at IS NOT NULL",
            Integer.class,
            subject
        )).isEqualTo(1);
    }

    private boolean consume(CountDownLatch start, UUID subject, String token) throws Exception {
        start.await();
        try {
            policy.requireAndConsume(subject, StepUpPurpose.MFA_DISABLE, token);
            return true;
        }
        catch (InvalidStepUpGrantException exception) {
            return false;
        }
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        jdbcTemplate.update("""
            INSERT INTO users (id,email,password_hash,role,status,email_verified_at,created_at,updated_at)
            VALUES (?,?,'hash','USER','ACTIVE',?,?,?)
            """, id, id + "@example.com", timestamp, timestamp, timestamp);
        return id;
    }

    private void insertGrant(UUID subject, StepUpPurpose purpose, String token, Instant expiresAt, Instant supersededAt) {
        Instant issuedAt = Instant.now().minusSeconds(1);
        jdbcTemplate.update("""
            INSERT INTO step_up_grants (
                id, subject_id, purpose, grant_digest, issued_at, expires_at,
                consumed_at, superseded_at
            ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?)
            """,
            UUID.randomUUID(), subject, purpose.value(), digestPort.digest(token).value(),
            Timestamp.from(issuedAt), Timestamp.from(expiresAt),
            supersededAt == null ? null : Timestamp.from(supersededAt)
        );
    }
}
