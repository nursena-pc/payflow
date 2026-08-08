package com.nursena.payflow.user.domain.model;

import java.util.Arrays;
import java.util.Objects;

public enum StepUpPurpose {
    MFA_DISABLE(
        "mfa-disable",
        StepUpActorKind.AUTHENTICATED_USER
    ),
    RECOVERY_CODE_ROTATION(
        "recovery-code-rotation",
        StepUpActorKind.AUTHENTICATED_USER
    ),
    MFA_AUTHENTICATOR_REPLACEMENT(
        "mfa-authenticator-replacement",
        StepUpActorKind.AUTHENTICATED_USER
    ),
    KAFKA_DEAD_LETTER_REPLAY(
        "kafka-dead-letter-replay",
        StepUpActorKind.PAYFLOW_OPERATOR
    ),
    KAFKA_DEAD_LETTER_DISCARD(
        "kafka-dead-letter-discard",
        StepUpActorKind.PAYFLOW_OPERATOR
    );

    private final String value;
    private final StepUpActorKind actorKind;

    StepUpPurpose(
        String value,
        StepUpActorKind actorKind
    ) {
        this.value = Objects.requireNonNull(
            value,
            "value must not be null"
        );
        this.actorKind = Objects.requireNonNull(
            actorKind,
            "actorKind must not be null"
        );
    }

    public static StepUpPurpose fromValue(
        String value
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                "step-up purpose is invalid"
            );
        }

        return Arrays.stream(values())
            .filter(candidate ->
                candidate.value.equals(value)
            )
            .findFirst()
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "step-up purpose is invalid"
                )
            );
    }

    public boolean isOperatorPurpose() {
        return actorKind
            == StepUpActorKind.PAYFLOW_OPERATOR;
    }

    public String value() {
        return value;
    }

    public StepUpActorKind actorKind() {
        return actorKind;
    }
}
