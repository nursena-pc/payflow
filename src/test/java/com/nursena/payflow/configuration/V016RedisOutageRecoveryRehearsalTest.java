package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
    .AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
    "payflow.security.login-rate-limit.enabled=true",
    "payflow.security.login-rate-limit.window=30s",
    "payflow.security.login-rate-limit.identity-limit=5",
    "payflow.security.login-rate-limit.client-limit=20",
    "payflow.security.abuse-protection.enabled=true",
    "payflow.security.abuse-protection.registration.enabled=false",
    "payflow.security.abuse-protection.password-recovery-request.enabled=true",
    "payflow.security.abuse-protection.password-recovery-request.dependency-failure-mode=FAIL_CLOSED",
    "spring.data.redis.connect-timeout=500ms",
    "spring.data.redis.timeout=500ms",
    "payflow.mail.outbox.polling.enabled=false",
    "payflow.outbox.polling.enabled=false",
    "payflow.event-processing.transfer-completed.enabled=false",
    "payflow.event-processing.transfer-completed.dead-letter-intake.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class V016RedisOutageRecoveryRehearsalTest {

    private static final int REDIS_PORT = 6379;

    private static final UUID USER_ID =
        UUID.fromString(
            "91000000-0000-0000-0000-000000000101"
        );

    private static final String USER_EMAIL =
        "v016.redis.recovery@example.invalid";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Container
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(
            DockerImageName.parse(
                "redis:8-alpine"
            )
        )
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.data.redis.host",
            REDIS::getHost
        );

        registry.add(
            "spring.data.redis.port",
            () ->
                REDIS.getMappedPort(
                    REDIS_PORT
                )
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldFailClosedAndRecoverWithoutDurableStateRepair()
        throws Exception {

        cleanDurableState();
        insertVerifiedSyntheticUser();

        String durableFingerprint =
            durableUserFingerprint();

        int mappedRedisPort =
            REDIS.getMappedPort(
                REDIS_PORT
            );

        assertHostPortPong(
            mappedRedisPort,
            "healthy baseline"
        );

        login(
            "healthy.login@example.invalid"
        )
            .andExpect(status().isUnauthorized());

        passwordRecovery(USER_EMAIL)
            .andExpect(status().isAccepted());

        assertThat(passwordRecoveryCredentialCount())
            .isEqualTo(1);
        assertThat(mailOutboxCount())
            .isEqualTo(1);

        clearRecoverySideEffects();

        double loginFailuresBefore =
            counter(
                "payflow.auth.login.rate_limit.redis.failures",
                "operation",
                "evaluate"
            );

        double abuseFailuresBefore =
            counter(
                "payflow.security.abuse_protection.redis.failures",
                "workflow",
                "password-recovery-request",
                "failure_mode",
                "fail_closed"
            );

        pauseRedis();

        try {
            awaitHostPortUnavailable(
                mappedRedisPort
            );
            MvcResult failedLogin =
                login(
                    "redis.down@example.invalid"
                )
                    .andExpect(
                        status()
                            .isServiceUnavailable()
                    )
                    .andExpect(
                        jsonPath("$.status")
                            .value(503)
                    )
                    .andExpect(
                        jsonPath("$.code")
                            .value(
                                "LOGIN_RATE_LIMIT_UNAVAILABLE"
                            )
                    )
                    .andExpect(
                        jsonPath("$.message")
                            .value(
                                "Login protection is "
                                    + "temporarily unavailable."
                            )
                    )
                    .andReturn();

            String responseBody =
                failedLogin
                    .getResponse()
                    .getContentAsString();

            assertThat(responseBody)
                .doesNotContain(
                    "127.0.0.1",
                    Integer.toString(
                        REDIS.getMappedPort(
                            REDIS_PORT
                        )
                    ),
                    "redis://",
                    "RedisConnection",
                    "Connection refused"
                );

            /*
             * Password recovery intentionally keeps its
             * public anti-enumeration response generic.
             * FAIL_CLOSED means no lookup/credential/mail
             * side effect occurs when Redis is unavailable.
             */
            passwordRecovery(USER_EMAIL)
                .andExpect(status().isAccepted());

            assertThat(passwordRecoveryCredentialCount())
                .isZero();
            assertThat(mailOutboxCount())
                .isZero();
            assertThat(durableUserFingerprint())
                .isEqualTo(durableFingerprint);

            assertThat(
                counter(
                    "payflow.auth.login.rate_limit.redis.failures",
                    "operation",
                    "evaluate"
                )
            )
                .isGreaterThan(loginFailuresBefore);

            assertThat(
                counter(
                    "payflow.security.abuse_protection.redis.failures",
                    "workflow",
                    "password-recovery-request",
                    "failure_mode",
                    "fail_closed"
                )
            )
                .isGreaterThan(abuseFailuresBefore);
        } finally {
            unpauseRedis();
        }

        awaitHostPortPong(
            mappedRedisPort
        );

        awaitApplicationLoginRecovery();

        login(
            "redis.recovered@example.invalid"
        )
            .andExpect(status().isUnauthorized());

        passwordRecovery(USER_EMAIL)
            .andExpect(status().isAccepted());

        assertThat(passwordRecoveryCredentialCount())
            .isEqualTo(1);
        assertThat(mailOutboxCount())
            .isEqualTo(1);
        assertThat(durableUserFingerprint())
            .isEqualTo(durableFingerprint);

        assertThat(userCount())
            .isEqualTo(1);
        assertThat(paymentTransactionCount())
            .isZero();
        assertThat(ledgerEntryCount())
            .isZero();
    }

    private org.springframework.test.web.servlet.ResultActions
    login(
        String email
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(
                        "203.0.113.101"
                    );
                    return request;
                })
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "email": "%s",
                      "password": "WrongPassword123!"
                    }
                    """.formatted(email)
                )
        );
    }

    private org.springframework.test.web.servlet.ResultActions
    passwordRecovery(
        String email
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/auth/password-recovery/requests"
            )
                .with(request -> {
                    request.setRemoteAddr(
                        "203.0.113.102"
                    );
                    return request;
                })
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "email": "%s"
                    }
                    """.formatted(email)
                )
        );
    }

    private void cleanDurableState() {
        jdbcTemplate.update(
            "DELETE FROM mail_outbox_messages"
        );
        jdbcTemplate.update(
            "DELETE FROM account_action_credentials"
        );
        jdbcTemplate.update(
            "DELETE FROM users"
        );
    }

    private void clearRecoverySideEffects() {
        jdbcTemplate.update(
            "DELETE FROM mail_outbox_messages"
        );
        jdbcTemplate.update(
            "DELETE FROM account_action_credentials"
        );
    }

    private void insertVerifiedSyntheticUser() {
        jdbcTemplate.update(
            """
            INSERT INTO users (
                id,
                email,
                password_hash,
                role,
                status,
                created_at,
                updated_at,
                email_verified_at
            )
            VALUES (
                ?, ?, ?,
                'USER',
                'ACTIVE',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            """,
            USER_ID,
            USER_EMAIL,
            "synthetic-unused-password-hash"
        );
    }

    private String durableUserFingerprint() {
        return jdbcTemplate.queryForObject(
            """
            SELECT md5(
                id::text
                || '|'
                || email
                || '|'
                || password_hash
                || '|'
                || role
                || '|'
                || status
                || '|'
                || (email_verified_at IS NOT NULL)::text
            )
            FROM users
            WHERE id = ?
            """,
            String.class,
            USER_ID
        );
    }

    private int passwordRecoveryCredentialCount() {
        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM account_action_credentials
                WHERE user_id = ?
                  AND purpose = 'PASSWORD_RECOVERY'
                """,
                Integer.class,
                USER_ID
            );

        return count == null ? 0 : count;
    }

    private int mailOutboxCount() {
        Integer count =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mail_outbox_messages",
                Integer.class
            );

        return count == null ? 0 : count;
    }

    private int userCount() {
        return count("users");
    }

    private int paymentTransactionCount() {
        return count("payment_transactions");
    }

    private int ledgerEntryCount() {
        return count("ledger_entries");
    }

    private int count(String table) {
        Integer count =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Integer.class
            );

        return count == null ? 0 : count;
    }

    private double counter(
        String name,
        String... tags
    ) {
        Counter counter =
            meterRegistry
                .find(name)
                .tags(tags)
                .counter();

        return counter == null
            ? 0.0
            : counter.count();
    }

    private static void pauseRedis() {
        dockerClient()
            .pauseContainerCmd(
                REDIS.getContainerId()
            )
            .exec();
    }

    private static void unpauseRedis() {
        dockerClient()
            .unpauseContainerCmd(
                REDIS.getContainerId()
            )
            .exec();
    }

    private void awaitApplicationLoginRecovery()
        throws Exception {

        long deadline =
            System.nanoTime()
                + Duration.ofSeconds(60)
                    .toNanos();

        int attempt = 0;
        int lastStatus = -1;

        while (
            System.nanoTime() < deadline
        ) {
            attempt++;

            lastStatus =
                login(
                    "redis.recovery."
                        + attempt
                        + "@example.invalid"
                )
                    .andReturn()
                    .getResponse()
                    .getStatus();

            if (lastStatus == 401) {
                return;
            }

            assertThat(lastStatus)
                .as(
                    "Recovery polling must remain "
                        + "fail-closed until Redis "
                        + "becomes usable again"
                )
                .isEqualTo(503);

            TimeUnit.MILLISECONDS.sleep(500);
        }

        throw new AssertionError(
            "Redis host endpoint recovered but "
                + "the running PayFlow login limiter "
                + "did not recover within 60 seconds. "
                + "Last HTTP status="
                + lastStatus
        );
    }

    private static void awaitHostPortUnavailable(
        int port
    ) throws Exception {

        long deadline =
            System.nanoTime()
                + Duration.ofSeconds(10)
                    .toNanos();

        while (
            System.nanoTime() < deadline
        ) {
            try {
                assertHostPortPong(
                    port,
                    "paused"
                );
            } catch (Exception | AssertionError expected) {
                return;
            }

            TimeUnit.MILLISECONDS.sleep(100);
        }

        throw new AssertionError(
            "Mapped Redis endpoint continued answering "
                + "PING while the isolated rehearsal "
                + "container was paused."
        );
    }

    private static void awaitHostPortPong(
        int port
    ) throws Exception {

        long deadline =
            System.nanoTime()
                + Duration.ofSeconds(30)
                    .toNanos();

        Throwable lastFailure = null;

        while (
            System.nanoTime() < deadline
        ) {
            try {
                assertHostPortPong(
                    port,
                    "post-unpause"
                );
                return;
            } catch (Throwable failure) {
                lastFailure = failure;
            }

            TimeUnit.MILLISECONDS.sleep(250);
        }

        throw new AssertionError(
            "Mapped Redis endpoint did not recover "
                + "after the isolated container "
                + "was unpaused.",
            lastFailure
        );
    }

    private static void assertHostPortPong(
        int port,
        String phase
    ) throws Exception {

        try (Socket socket = new Socket()) {
            socket.connect(
                new InetSocketAddress(
                    REDIS.getHost(),
                    port
                ),
                750
            );

            socket.setSoTimeout(750);

            OutputStream output =
                socket.getOutputStream();

            output.write(
                "*1\r\n$4\r\nPING\r\n"
                    .getBytes(
                        StandardCharsets.US_ASCII
                    )
            );

            output.flush();

            InputStream input =
                socket.getInputStream();

            byte[] response =
                input.readNBytes(7);

            assertThat(
                new String(
                    response,
                    StandardCharsets.US_ASCII
                )
            )
                .as(
                    "host-port Redis PING during "
                        + phase
                )
                .isEqualTo("+PONG\r\n");
        }
    }

    private static DockerClient dockerClient() {
        return DockerClientFactory
            .instance()
            .client();
    }
}
