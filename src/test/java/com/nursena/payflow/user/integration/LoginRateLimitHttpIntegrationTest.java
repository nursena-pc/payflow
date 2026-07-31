package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
    .AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request
    .RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    properties = {
        "payflow.security.login-rate-limit.enabled=true",
        "payflow.security.login-rate-limit.window=30s",
        "payflow.security.login-rate-limit.identity-limit=2",
        "payflow.security.login-rate-limit.client-limit=3",
        "payflow.security.client-context.trusted-proxy-cidrs[0]=10.0.0.0/8",
        "payflow.security.client-context.max-forwarded-header-length=4096",
        "payflow.security.client-context.max-forwarded-hops=8",
        "payflow.security.refresh-session.refresh-token-ttl=7d",
        "payflow.security.refresh-session.family-ttl=30d"
    }
)
@AutoConfigureMockMvc
@Testcontainers
class LoginRateLimitHttpIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static final String LOGIN_PATH =
        "/api/v1/auth/login";

    private static final String REGISTER_PATH =
        "/api/v1/auth/register";

    private static final String PASSWORD =
        "StrongPassword123!";

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
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanState() {
        redisTemplate.execute(
            (RedisCallback<Void>) connection -> {
                connection
                    .serverCommands()
                    .flushDb();

                return null;
            }
        );

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
    void shouldReturn429WithRedisRetryAfterForIdentityLimit()
        throws Exception {

        String email =
            uniqueEmail(
                "identity-limit"
            );

        String clientAddress =
            "203.0.113.21";

        performLogin(
            email,
            "WrongPassword123!",
            clientAddress
        )
            .andExpect(
                status().isUnauthorized()
            );

        performLogin(
            email,
            "WrongPassword123!",
            clientAddress
        )
            .andExpect(
                status().isUnauthorized()
            );

        MvcResult blocked =
            performLogin(
                email,
                "WrongPassword123!",
                clientAddress
            )
                .andExpect(
                    status()
                        .isTooManyRequests()
                )
                .andExpect(
                    header().exists(
                        "Retry-After"
                    )
                )
                .andExpect(
                    jsonPath("$.status")
                        .value(429)
                )
                .andExpect(
                    jsonPath("$.code")
                        .value(
                            "LOGIN_RATE_LIMIT_EXCEEDED"
                        )
                )
                .andExpect(
                    jsonPath("$.message")
                        .value(
                            "Too many login attempts. "
                                + "Try again later."
                        )
                )
                .andExpect(
                    jsonPath("$.path")
                        .value(LOGIN_PATH)
                )
                .andReturn();

        String identityKey =
            identityKey(email);

        String clientKey =
            clientKey(clientAddress);

        assertThat(
            redisTemplate.opsForValue()
                .get(identityKey)
        )
            .isEqualTo("3");

        assertThat(
            redisTemplate.opsForValue()
                .get(clientKey)
        )
            .isEqualTo("3");

        assertRetryAfterMatchesRedisTtl(
            blocked,
            identityKey
        );
    }

    @Test
    void shouldEnforceClientLimitAcrossDistinctIdentities()
        throws Exception {

        String clientAddress =
            "203.0.113.22";

        for (
            int attempt = 1;
            attempt <= 3;
            attempt++
        ) {
            performLogin(
                uniqueEmail(
                    "client-limit-" + attempt
                ),
                "WrongPassword123!",
                clientAddress
            )
                .andExpect(
                    status().isUnauthorized()
                );
        }

        String blockedEmail =
            uniqueEmail(
                "client-limit-4"
            );

        MvcResult blocked =
            performLogin(
                blockedEmail,
                "WrongPassword123!",
                clientAddress
            )
                .andExpect(
                    status()
                        .isTooManyRequests()
                )
                .andExpect(
                    header().exists(
                        "Retry-After"
                    )
                )
                .andExpect(
                    jsonPath("$.code")
                        .value(
                            "LOGIN_RATE_LIMIT_EXCEEDED"
                        )
                )
                .andReturn();

        String clientKey =
            clientKey(clientAddress);

        assertThat(
            redisTemplate.opsForValue()
                .get(clientKey)
        )
            .isEqualTo("4");

        assertThat(
            redisTemplate.opsForValue()
                .get(
                    identityKey(
                        blockedEmail
                    )
                )
        )
            .isEqualTo("1");

        assertRetryAfterMatchesRedisTtl(
            blocked,
            clientKey
        );
    }

    @Test
    void shouldGroupTrustedProxyRequestsByEffectiveClient()
        throws Exception {

        String directProxy =
            "10.0.0.9";

        String effectiveClient =
            "198.51.100.44";

        for (
            int attempt = 1;
            attempt <= 3;
            attempt++
        ) {
            performForwardedLogin(
                uniqueEmail(
                    "trusted-client-" + attempt
                ),
                "WrongPassword123!",
                directProxy,
                effectiveClient
            )
                .andExpect(
                    status().isUnauthorized()
                );
        }

        performForwardedLogin(
            uniqueEmail(
                "trusted-client-blocked"
            ),
            "WrongPassword123!",
            directProxy,
            effectiveClient
        )
            .andExpect(
                status().isTooManyRequests()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "LOGIN_RATE_LIMIT_EXCEEDED"
                    )
            );

        assertThat(
            redisTemplate.opsForValue()
                .get(
                    clientKey(
                        effectiveClient
                    )
                )
        )
            .isEqualTo("4");

        assertThat(
            redisTemplate.hasKey(
                clientKey(
                    directProxy
                )
            )
        )
            .isFalse();
    }

    @Test
    void shouldIgnoreSpoofedHeaderFromUntrustedPeers()
        throws Exception {

        String spoofedClient =
            "198.51.100.55";

        for (
            int attempt = 1;
            attempt <= 4;
            attempt++
        ) {
            String directPeer =
                "203.0.113."
                    + (
                        40 + attempt
                    );

            performForwardedLogin(
                uniqueEmail(
                    "spoofed-client-" + attempt
                ),
                "WrongPassword123!",
                directPeer,
                spoofedClient
            )
                .andExpect(
                    status().isUnauthorized()
                );

            assertThat(
                redisTemplate.opsForValue()
                    .get(
                        clientKey(
                            directPeer
                        )
                    )
            )
                .isEqualTo("1");
        }

        assertThat(
            redisTemplate.hasKey(
                clientKey(
                    spoofedClient
                )
            )
        )
            .isFalse();
    }

    @Test
    void shouldResetIdentityAfterSuccessfulLoginOnly()
        throws Exception {

        String email =
            uniqueEmail(
                "successful-reset"
            );

        String clientAddress =
            "203.0.113.23";

        registerUser(
            email,
            PASSWORD
        );

        performLogin(
            email,
            "WrongPassword123!",
            clientAddress
        )
            .andExpect(
                status().isUnauthorized()
            );

        String identityKey =
            identityKey(email);

        String clientKey =
            clientKey(clientAddress);

        assertThat(
            redisTemplate.opsForValue()
                .get(identityKey)
        )
            .isEqualTo("1");

        performLogin(
            email,
            PASSWORD,
            clientAddress
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.accessToken")
                    .isNotEmpty()
            )
            .andExpect(
                jsonPath("$.refreshToken")
                    .isNotEmpty()
            );

        assertThat(
            redisTemplate.hasKey(
                identityKey
            )
        )
            .isFalse();

        assertThat(
            redisTemplate.opsForValue()
                .get(clientKey)
        )
            .isEqualTo("2");

        assertThat(
            redisTemplate.getExpire(
                clientKey,
                TimeUnit.SECONDS
            )
        )
            .isPositive();
    }

    private ResultActions performLogin(
        String email,
        String password,
        String clientAddress
    ) throws Exception {
        return mockMvc.perform(
            post(LOGIN_PATH)
                .with(
                    remoteAddress(
                        clientAddress
                    )
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    objectMapper
                        .writeValueAsString(
                            new LoginRequest(
                                email,
                                password
                            )
                        )
                )
        );
    }

    private ResultActions performForwardedLogin(
        String email,
        String password,
        String directPeer,
        String effectiveClient
    ) throws Exception {
        return mockMvc.perform(
            post(LOGIN_PATH)
                .with(
                    remoteAddress(
                        directPeer
                    )
                )
                .header(
                    "Forwarded",
                    "for=" + effectiveClient
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    objectMapper
                        .writeValueAsString(
                            new LoginRequest(
                                email,
                                password
                            )
                        )
                )
        );
    }

    private void registerUser(
        String email,
        String password
    ) throws Exception {
        mockMvc.perform(
                post(REGISTER_PATH)
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper
                            .writeValueAsString(
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
    }

    private void assertRetryAfterMatchesRedisTtl(
        MvcResult result,
        String key
    ) {
        long retryAfter =
            Long.parseLong(
                result
                    .getResponse()
                    .getHeader(
                        "Retry-After"
                    )
            );

        Long currentTtl =
            redisTemplate.getExpire(
                key,
                TimeUnit.SECONDS
            );

        assertThat(retryAfter)
            .isPositive()
            .isLessThanOrEqualTo(30L);

        assertThat(currentTtl)
            .isPositive();

        assertThat(retryAfter)
            .isBetween(
                currentTtl,
                currentTtl + 2L
            );
    }

    private static RequestPostProcessor
    remoteAddress(
        String address
    ) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static String identityKey(
        String email
    ) {
        return key(
            "payflow:security:login:identity:",
            email
                .trim()
                .toLowerCase(
                    Locale.ROOT
                )
        );
    }

    private static String clientKey(
        String clientAddress
    ) {
        return key(
            "payflow:security:login:client:",
            clientAddress
                .trim()
                .toLowerCase(
                    Locale.ROOT
                )
        );
    }

    private static String key(
        String prefix,
        String value
    ) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance(
                    "SHA-256"
                );

            return prefix
                + HexFormat.of()
                    .formatHex(
                        digest.digest(
                            value.getBytes(
                                StandardCharsets.UTF_8
                            )
                        )
                    );
        } catch (
            NoSuchAlgorithmException exception
        ) {
            throw new IllegalStateException(
                "SHA-256 is not available",
                exception
            );
        }
    }

    private static String uniqueEmail(
        String prefix
    ) {
        return prefix
            + "-"
            + UUID.randomUUID()
            + "@example.com";
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
}
