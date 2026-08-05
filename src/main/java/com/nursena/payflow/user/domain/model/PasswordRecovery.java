package com.nursena.payflow.user.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class PasswordRecovery {

    private PasswordRecovery() {
    }

    public static void replacePassword(
        User user,
        String replacementPasswordHash,
        Instant recoveredAt
    ) {
        User checkedUser = Objects.requireNonNull(
            user,
            "user must not be null"
        );

        checkedUser.changePassword(
            replacementPasswordHash,
            recoveredAt
        );
    }
}
