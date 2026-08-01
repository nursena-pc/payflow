package com.nursena.payflow.observability.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public final class CorrelationIdPolicy {

    public static final int MAXIMUM_LENGTH =
        64;

    private static final Pattern ACCEPTED_VALUE =
        Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{0,63}"
        );

    public boolean isAccepted(
        String candidate
    ) {
        return candidate != null
            && ACCEPTED_VALUE
                .matcher(candidate)
                .matches();
    }

    public String effective(
        String inboundCandidate,
        CorrelationIdGenerator generator
    ) {
        Objects.requireNonNull(
            generator,
            "correlation ID generator must not be null"
        );

        if (isAccepted(inboundCandidate)) {
            return inboundCandidate;
        }

        String generated =
            Objects.requireNonNull(
                generator.generate(),
                "generated correlation ID must not be null"
            );

        if (!isAccepted(generated)) {
            throw new IllegalStateException(
                "generated correlation ID violates the policy"
            );
        }

        return generated;
    }
}