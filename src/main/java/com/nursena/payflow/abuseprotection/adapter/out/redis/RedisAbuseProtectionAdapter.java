package com.nursena.payflow.abuseprotection.adapter.out.redis;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.nursena.payflow.abuseprotection.application.exception.AbuseProtectionUnavailableException;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionFailureMode;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionPolicy;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionPolicyProvider;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDecision;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDimension;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionEnforcementPort;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

final class RedisAbuseProtectionAdapter
    implements AbuseProtectionEnforcementPort {

    private static final int RESULT_SIZE = 3;
    private static final int IDENTITY_BLOCKED_INDEX = 0;
    private static final int CLIENT_BLOCKED_INDEX = 1;
    private static final int RETRY_AFTER_INDEX = 2;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> script;
    private final AbuseProtectionPolicyProvider policyProvider;
    private final AbuseProtectionMetrics metrics;

    RedisAbuseProtectionAdapter(
        StringRedisTemplate redisTemplate,
        RedisScript<List<Long>> script,
        AbuseProtectionPolicyProvider policyProvider,
        AbuseProtectionMetrics metrics
    ) {
        this.redisTemplate = Objects.requireNonNull(
            redisTemplate,
            "redisTemplate must not be null"
        );

        this.script = Objects.requireNonNull(
            script,
            "script must not be null"
        );

        this.policyProvider = Objects.requireNonNull(
            policyProvider,
            "policyProvider must not be null"
        );

        this.metrics = Objects.requireNonNull(
            metrics,
            "metrics must not be null"
        );
    }

    @Override
    public AbuseProtectionDecision evaluate(
        AbuseProtectionRequest request
    ) {
        AbuseProtectionRequest validatedRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        AbuseProtectionPolicy policy =
            Objects.requireNonNull(
                policyProvider.policyFor(
                    validatedRequest.workflow()
                ),
                "policy must not be null"
            );

        if (!policy.enabled()) {
            metrics.recordDisabled(
                validatedRequest.workflow()
            );
            return AbuseProtectionDecision.allowed();
        }

        List<String> keys = List.of(
            AbuseProtectionKeyFactory.identityKey(
                validatedRequest.workflow(),
                validatedRequest.normalizedIdentity()
            ),
            AbuseProtectionKeyFactory.clientKey(
                validatedRequest.workflow(),
                validatedRequest
                    .effectiveClientAddress()
                    .value()
            )
        );

        AbuseProtectionDecision decision;

        try {
            List<Long> result = redisTemplate.execute(
                script,
                keys,
                Long.toString(
                    expirationSeconds(policy.window())
                ),
                Integer.toString(policy.identityLimit()),
                Integer.toString(policy.clientLimit())
            );

            decision = parseDecision(result);
        } catch (RuntimeException exception) {
            metrics.recordRedisFailure(
                validatedRequest.workflow(),
                policy.dependencyFailureMode()
            );

            if (
                policy.dependencyFailureMode()
                    == AbuseProtectionFailureMode.FAIL_OPEN
            ) {
                metrics.recordDependencyBypass(
                    validatedRequest.workflow()
                );
                return AbuseProtectionDecision.allowed();
            }

            throw new AbuseProtectionUnavailableException(
                validatedRequest.workflow(),
                policy.dependencyFailureMode(),
                exception
            );
        }

        metrics.recordDecision(
            validatedRequest.workflow(),
            decision
        );

        return decision;
    }

    private static long expirationSeconds(Duration window) {
        long millis = window.toMillis();
        return Math.max(1L, (millis + 999L) / 1000L);
    }

    private static AbuseProtectionDecision parseDecision(
        List<Long> result
    ) {
        if (result == null || result.size() != RESULT_SIZE) {
            throw invalidResult();
        }

        long identityFlag = numericValue(
            result.get(IDENTITY_BLOCKED_INDEX)
        );

        long clientFlag = numericValue(
            result.get(CLIENT_BLOCKED_INDEX)
        );

        long retryAfter = numericValue(
            result.get(RETRY_AFTER_INDEX)
        );

        if (
            (identityFlag != 0L && identityFlag != 1L)
                || (clientFlag != 0L && clientFlag != 1L)
        ) {
            throw invalidResult();
        }

        boolean identityBlocked = identityFlag == 1L;
        boolean clientBlocked = clientFlag == 1L;

        if (!identityBlocked && !clientBlocked) {
            if (retryAfter != 0L) {
                throw invalidResult();
            }

            return AbuseProtectionDecision.allowed();
        }

        if (retryAfter <= 0L) {
            throw invalidResult();
        }

        return AbuseProtectionDecision.blocked(
            blockedDimension(
                identityBlocked,
                clientBlocked
            ),
            Duration.ofSeconds(retryAfter)
        );
    }

    private static long numericValue(Long value) {
        if (value == null) {
            throw invalidResult();
        }

        return value;
    }

    private static AbuseProtectionDimension blockedDimension(
        boolean identityBlocked,
        boolean clientBlocked
    ) {
        if (identityBlocked && clientBlocked) {
            return AbuseProtectionDimension.BOTH;
        }

        if (identityBlocked) {
            return AbuseProtectionDimension.IDENTITY;
        }

        return AbuseProtectionDimension.CLIENT;
    }

    private static IllegalStateException invalidResult() {
        return new IllegalStateException(
            "Redis script returned an invalid result"
        );
    }
}
