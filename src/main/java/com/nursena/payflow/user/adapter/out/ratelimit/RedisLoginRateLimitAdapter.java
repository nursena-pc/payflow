package com.nursena.payflow.user.adapter.out.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.nursena.payflow.user.application.exception.LoginRateLimitUnavailableException;
import com.nursena.payflow.user.application.port.out.LoginRateLimitDecision;
import com.nursena.payflow.user.application.port.out.LoginRateLimitDimension;
import com.nursena.payflow.user.application.port.out.LoginRateLimitPort;
import com.nursena.payflow.user.application.port.out.LoginRateLimitRequest;
import com.nursena.payflow.user.domain.model.EmailAddress;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

final class RedisLoginRateLimitAdapter
    implements LoginRateLimitPort {

    private static final int RESULT_SIZE = 3;

    private static final int IDENTITY_BLOCKED_INDEX = 0;

    private static final int CLIENT_BLOCKED_INDEX = 1;

    private static final int RETRY_AFTER_INDEX = 2;

    private final StringRedisTemplate redisTemplate;

    private final RedisScript<List<Long>> script;

    private final LoginRateLimitProperties properties;

    RedisLoginRateLimitAdapter(
        StringRedisTemplate redisTemplate,
        RedisScript<List<Long>> script,
        LoginRateLimitProperties properties
    ) {
        this.redisTemplate =
            Objects.requireNonNull(
                redisTemplate,
                "redisTemplate must not be null"
            );

        this.script =
            Objects.requireNonNull(
                script,
                "script must not be null"
            );

        this.properties =
            Objects.requireNonNull(
                properties,
                "properties must not be null"
            );
    }

    @Override
    public LoginRateLimitDecision evaluate(
        LoginRateLimitRequest request
    ) {
        Objects.requireNonNull(
            request,
            "request must not be null"
        );

        if (!properties.enabled()) {
            return LoginRateLimitDecision.allowed();
        }

        List<String> keys =
            List.of(
                LoginRateLimitKeyFactory.identityKey(
                    request.identity()
                ),
                LoginRateLimitKeyFactory.clientKey(
                    request.clientAddress()
                )
            );

        try {
            List<Long> result =
                redisTemplate.execute(
                    script,
                    keys,
                    Long.toString(
                        properties.window().toSeconds()
                    ),
                    Integer.toString(
                        properties.identityLimit()
                    ),
                    Integer.toString(
                        properties.clientLimit()
                    )
                );

            return parseDecision(result);
        } catch (
            LoginRateLimitUnavailableException exception
        ) {
            throw exception;
        } catch (
            RuntimeException exception
        ) {
            throw new LoginRateLimitUnavailableException(
                exception
            );
        }
    }

    @Override
    public void resetIdentity(
        EmailAddress identity
    ) {
        Objects.requireNonNull(
            identity,
            "identity must not be null"
        );

        if (!properties.enabled()) {
            return;
        }

        try {
            Boolean deleted =
                redisTemplate.delete(
                    LoginRateLimitKeyFactory
                        .identityKey(identity)
                );

            if (deleted == null) {
                throw new IllegalStateException(
                    "Redis delete returned no result"
                );
            }
        } catch (
            RuntimeException exception
        ) {
            throw new LoginRateLimitUnavailableException(
                exception
            );
        }
    }

    private static LoginRateLimitDecision parseDecision(
        List<Long> result
    ) {
        if (
            result == null
                || result.size() != RESULT_SIZE
        ) {
            throw unavailableResult();
        }

        boolean identityBlocked =
            numericValue(
                result.get(
                    IDENTITY_BLOCKED_INDEX
                )
            ) == 1L;

        boolean clientBlocked =
            numericValue(
                result.get(
                    CLIENT_BLOCKED_INDEX
                )
            ) == 1L;

        long retryAfterSeconds =
            numericValue(
                result.get(
                    RETRY_AFTER_INDEX
                )
            );

        if (
            !identityBlocked
                && !clientBlocked
        ) {
            return LoginRateLimitDecision.allowed();
        }

        LoginRateLimitDimension dimension =
            blockedDimension(
                identityBlocked,
                clientBlocked
            );

        return LoginRateLimitDecision.blocked(
            dimension,
            Duration.ofSeconds(
                Math.max(
                    1L,
                    retryAfterSeconds
                )
            )
        );
    }

    private static long numericValue(
        Long value
    ) {
        if (value == null) {
            throw unavailableResult();
        }

        return value;
    }

    private static LoginRateLimitDimension
    blockedDimension(
        boolean identityBlocked,
        boolean clientBlocked
    ) {
        if (
            identityBlocked
                && clientBlocked
        ) {
            return LoginRateLimitDimension.BOTH;
        }

        if (identityBlocked) {
            return LoginRateLimitDimension.IDENTITY;
        }

        return LoginRateLimitDimension.CLIENT;
    }

    private static LoginRateLimitUnavailableException
    unavailableResult() {
        return new LoginRateLimitUnavailableException(
            new IllegalStateException(
                "Redis script returned an invalid result"
            )
        );
    }
}
