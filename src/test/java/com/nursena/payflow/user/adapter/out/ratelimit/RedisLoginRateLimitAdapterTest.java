package com.nursena.payflow.user.adapter.out.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import com.nursena.payflow.user.application.exception.LoginRateLimitUnavailableException;
import com.nursena.payflow.user.application.port.out.LoginRateLimitDecision;
import com.nursena.payflow.user.application.port.out.LoginRateLimitDimension;
import com.nursena.payflow.user.application.port.out.LoginRateLimitRequest;
import com.nursena.payflow.user.domain.model.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

class RedisLoginRateLimitAdapterTest {

    private static final EmailAddress IDENTITY =
        EmailAddress.of(
            "nursena@example.com"
        );

    private static final LoginRateLimitProperties
        ENABLED_PROPERTIES =
        new LoginRateLimitProperties(
            true,
            Duration.ofMinutes(15),
            5,
            20
        );

    private RecordingStringRedisTemplate
        redisTemplate;

    private RedisLoginRateLimitAdapter adapter;

    @BeforeEach
    void setUp() {
        redisTemplate =
            new RecordingStringRedisTemplate();

        adapter =
            new RedisLoginRateLimitAdapter(
                redisTemplate,
                script(),
                ENABLED_PROPERTIES
            );
    }

    @Test
    void shouldAllowRequestAndUseHashedKeys() {
        redisTemplate.scriptResult =
            List.of(
                0L,
                0L,
                0L
            );

        LoginRateLimitDecision decision =
            adapter.evaluate(
                request()
            );

        assertThat(decision.isAllowed())
            .isTrue();

        assertThat(redisTemplate.executions)
            .isEqualTo(1);

        assertThat(redisTemplate.keys)
            .hasSize(2)
            .allSatisfy(key ->
                assertThat(key)
                    .doesNotContain(
                        "nursena@example.com",
                        "127.0.0.1"
                    )
            );

        assertThat(redisTemplate.arguments)
            .containsExactly(
                "900",
                "5",
                "20"
            );
    }

    @Test
    void shouldMapIdentityBlockingDecision() {
        redisTemplate.scriptResult =
            List.of(
                1L,
                0L,
                840L
            );

        LoginRateLimitDecision decision =
            adapter.evaluate(
                request()
            );

        assertThat(decision.isAllowed())
            .isFalse();

        assertThat(decision.blockedDimension())
            .isEqualTo(
                LoginRateLimitDimension.IDENTITY
            );

        assertThat(decision.retryAfter())
            .isEqualTo(
                Duration.ofSeconds(840)
            );
    }

    @Test
    void shouldMapCombinedBlockingDecision() {
        redisTemplate.scriptResult =
            List.of(
                1L,
                1L,
                900L
            );

        LoginRateLimitDecision decision =
            adapter.evaluate(
                request()
            );

        assertThat(decision.blockedDimension())
            .isEqualTo(
                LoginRateLimitDimension.BOTH
            );

        assertThat(decision.retryAfter())
            .isEqualTo(
                Duration.ofMinutes(15)
            );
    }

    @Test
    void shouldSkipRedisWhenDisabled() {
        RedisLoginRateLimitAdapter disabledAdapter =
            new RedisLoginRateLimitAdapter(
                redisTemplate,
                script(),
                new LoginRateLimitProperties(
                    false,
                    Duration.ofMinutes(15),
                    5,
                    20
                )
            );

        LoginRateLimitDecision decision =
            disabledAdapter.evaluate(
                request()
            );

        disabledAdapter.resetIdentity(
            IDENTITY
        );

        assertThat(decision.isAllowed())
            .isTrue();

        assertThat(redisTemplate.executions)
            .isZero();

        assertThat(redisTemplate.deletedKey)
            .isNull();
    }

    @Test
    void shouldTranslateRedisEvaluationFailure() {
        IllegalStateException redisFailure =
            new IllegalStateException(
                "redis-host:6379 unavailable"
            );

        redisTemplate.executeFailure =
            redisFailure;

        assertThatThrownBy(() ->
            adapter.evaluate(
                request()
            )
        )
            .isInstanceOf(
                LoginRateLimitUnavailableException.class
            )
            .hasMessage(
                "Login protection is "
                    + "temporarily unavailable."
            )
            .hasCause(redisFailure)
            .hasMessageNotContaining(
                "redis-host"
            );
    }

    @Test
    void shouldRejectMalformedScriptResult() {
        redisTemplate.scriptResult =
            List.of(
                0L,
                0L
            );

        assertThatThrownBy(() ->
            adapter.evaluate(
                request()
            )
        )
            .isInstanceOf(
                LoginRateLimitUnavailableException.class
            )
            .hasMessage(
                "Login protection is "
                    + "temporarily unavailable."
            );
    }

    @Test
    void shouldResetOnlyHashedIdentityKey() {
        adapter.resetIdentity(
            IDENTITY
        );

        assertThat(redisTemplate.deletedKey)
            .startsWith(
                "payflow:security:login:"
                    + "identity:"
            )
            .doesNotContain(
                "nursena@example.com"
            );
    }

    @SuppressWarnings({
        "unchecked",
        "rawtypes"
    })
    private static RedisScript<List<Long>> script() {
        return (RedisScript) new DefaultRedisScript<>(
            "return {0, 0, 0}",
            List.class
        );
    }

    private static LoginRateLimitRequest request() {
        return new LoginRateLimitRequest(
            IDENTITY,
            "127.0.0.1"
        );
    }

    private static final class
    RecordingStringRedisTemplate
        extends StringRedisTemplate {

        private List<?> scriptResult =
            List.of(
                0L,
                0L,
                0L
            );

        private RuntimeException executeFailure;

        private int executions;

        private List<String> keys;

        private Object[] arguments;

        private String deletedKey;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(
            RedisScript<T> script,
            List<String> keys,
            Object... args
        ) {
            executions++;

            if (executeFailure != null) {
                throw executeFailure;
            }

            this.keys =
                List.copyOf(keys);

            this.arguments =
                args.clone();

            return (T) scriptResult;
        }

        @Override
        public Boolean delete(
            String key
        ) {
            deletedKey = key;
            return true;
        }
    }
}
