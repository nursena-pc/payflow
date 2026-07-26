package com.nursena.payflow.user.domain.model;

import java.util.Objects;
import java.util.UUID;

public record RefreshTokenRecordId(
    UUID value
) {

    public RefreshTokenRecordId {
        Objects.requireNonNull(
            value,
            "value must not be null"
        );
    }

    public static RefreshTokenRecordId of(
        UUID value
    ) {
        return new RefreshTokenRecordId(value);
    }
}
