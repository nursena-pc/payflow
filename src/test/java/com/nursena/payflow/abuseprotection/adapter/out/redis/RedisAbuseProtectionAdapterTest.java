package com.nursena.payflow.abuseprotection.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import com.nursena.payflow.abuseprotection.application.exception.AbuseProtectionUnavailableException;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionFailureMode;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionPolicy;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDecision;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDimension;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionRequest;
import com.nursena.payflow.clientcontext.domain.IpAddress;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

class RedisAbuseProtectionAdapterTest {

    private RecordingRedisTemplate redisTemplate;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        redisTemplate = new RecordingRedisTemplate();
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void shouldAllowAndUseOnlyHashedSensitiveKeys() {
        RedisAbuseProtectionAdapter adapter = adapter(
            policy(true, AbuseProtectionFailureMode.FAIL_CLOSED)
        );

        AbuseProtectionDecision decision =
            adapter.evaluate(request());

        assertThat(decision.isAllowed()).isTrue();
        assertThat(redisTemplate.keys)
            .hasSize(2)
            .allSatisfy(key ->
                assertThat(key).doesNotContain(
                    "nursena@example.com",
                    "203.0.113.10"
                )
            );
        assertThat(redisTemplate.arguments)
            .containsExactly("900", "5", "20");
        assertDecisionMetric(
            "allowed",
            "none",
            1.0
        );
    }

    @Test
    void shouldMapEveryBlockedDimension() {
        RedisAbuseProtectionAdapter adapter = adapter(
            policy(true, AbuseProtectionFailureMode.FAIL_CLOSED)
        );

        redisTemplate.result = List.of(1L, 0L, 20L);
        assertThat(adapter.evaluate(request()).blockedDimension())
            .isEqualTo(AbuseProtectionDimension.IDENTITY);

        redisTemplate.result = List.of(0L, 1L, 19L);
        assertThat(adapter.evaluate(request()).blockedDimension())
            .isEqualTo(AbuseProtectionDimension.CLIENT);

        redisTemplate.result = List.of(1L, 1L, 18L);
        assertThat(adapter.evaluate(request()).blockedDimension())
            .isEqualTo(AbuseProtectionDimension.BOTH);
    }

    @Test
    void shouldSkipRedisWhenPolicyIsDisabled() {
        AbuseProtectionDecision decision = adapter(
            policy(false, AbuseProtectionFailureMode.FAIL_CLOSED)
        ).evaluate(request());

        assertThat(decision.isAllowed()).isTrue();
        assertThat(redisTemplate.executions).isZero();
        assertDecisionMetric(
            "disabled",
            "none",
            1.0
        );
    }

    @Test
    void shouldFailClosedWithoutLeakingDependencyDetails() {
        IllegalStateException failure =
            new IllegalStateException("redis.internal:6379 unavailable");
        redisTemplate.failure = failure;

        assertThatThrownBy(() -> adapter(
            policy(true, AbuseProtectionFailureMode.FAIL_CLOSED)
        ).evaluate(request()))
            .isInstanceOf(AbuseProtectionUnavailableException.class)
            .hasMessage(
                "Abuse protection is temporarily unavailable."
            )
            .hasMessageNotContaining("redis.internal")
            .hasCause(failure)
            .satisfies(exception -> {
                AbuseProtectionUnavailableException unavailable =
                    (AbuseProtectionUnavailableException) exception;
                assertThat(unavailable.workflow())
                    .isEqualTo(AbuseProtectionWorkflow.REGISTRATION);
                assertThat(unavailable.failureMode())
                    .isEqualTo(AbuseProtectionFailureMode.FAIL_CLOSED);
            });

        assertRedisFailureMetric(
            "fail_closed",
            1.0
        );
        assertThat(
            meterRegistry.find(
                AbuseProtectionMetrics.DECISIONS_METRIC
            ).counters()
        ).isEmpty();
    }

    @Test
    void shouldFailOpenOnlyWhenPolicyExplicitlySelectsIt() {
        redisTemplate.failure =
            new IllegalStateException("unavailable");

        assertThat(adapter(
            policy(true, AbuseProtectionFailureMode.FAIL_OPEN)
        ).evaluate(request()).isAllowed()).isTrue();

        assertRedisFailureMetric(
            "fail_open",
            1.0
        );
        assertDecisionMetric(
            "dependency_bypass",
            "dependency_failure",
            1.0
        );
        assertThat(
            meterRegistry.find(
                AbuseProtectionMetrics.DECISIONS_METRIC
            ).tag("outcome", "allowed")
            .counters()
        ).isEmpty();
    }

    @Test
    void shouldTreatMalformedResultsAsDependencyFailures() {
        redisTemplate.result = List.of(0L, 0L, 7L);

        assertThatThrownBy(() -> adapter(
            policy(true, AbuseProtectionFailureMode.FAIL_CLOSED)
        ).evaluate(request()))
            .isInstanceOf(AbuseProtectionUnavailableException.class);
    }

    private void assertDecisionMetric(
        String outcome,
        String reason,
        double expectedCount
    ) {
        assertThat(
            meterRegistry
                .get(AbuseProtectionMetrics.DECISIONS_METRIC)
                .tag("workflow", "registration")
                .tag("outcome", outcome)
                .tag("reason", reason)
                .counter()
                .count()
        ).isEqualTo(expectedCount);
    }

    private void assertRedisFailureMetric(
        String failureMode,
        double expectedCount
    ) {
        assertThat(
            meterRegistry
                .get(AbuseProtectionMetrics.REDIS_FAILURES_METRIC)
                .tag("workflow", "registration")
                .tag("failure_mode", failureMode)
                .counter()
                .count()
        ).isEqualTo(expectedCount);
    }

    private RedisAbuseProtectionAdapter adapter(
        AbuseProtectionPolicy policy
    ) {
        return new RedisAbuseProtectionAdapter(
            redisTemplate,
            script(),
            workflow -> policy,
            new AbuseProtectionMetrics(meterRegistry)
        );
    }

    private static AbuseProtectionPolicy policy(
        boolean enabled,
        AbuseProtectionFailureMode failureMode
    ) {
        return new AbuseProtectionPolicy(
            enabled,
            Duration.ofMinutes(15),
            5,
            20,
            failureMode
        );
    }

    private static AbuseProtectionRequest request() {
        return new AbuseProtectionRequest(
            AbuseProtectionWorkflow.REGISTRATION,
            "nursena@example.com",
            IpAddress.parse("203.0.113.10")
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RedisScript<List<Long>> script() {
        return (RedisScript) new DefaultRedisScript<>(
            "return {0, 0, 0}",
            List.class
        );
    }

    private static final class RecordingRedisTemplate
        extends StringRedisTemplate {

        private List<?> result = List.of(0L, 0L, 0L);
        private RuntimeException failure;
        private int executions;
        private List<String> keys;
        private Object[] arguments;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(
            RedisScript<T> script,
            List<String> keys,
            Object... args
        ) {
            executions++;

            if (failure != null) {
                throw failure;
            }

            this.keys = List.copyOf(keys);
            this.arguments = args.clone();
            return (T) result;
        }
    }
}
