package com.nursena.payflow.transaction.domain.model;

import java.util.Objects;

import com.nursena.payflow.transaction.domain.exception.InvalidIdempotencyKeyException;

public record IdempotencyKey(String value) {

    public static final int MAX_LENGTH = 100;

    public IdempotencyKey {
        Objects.requireNonNull(
            value,
            "value must not be null"
        );

        value = value.trim();

        if (value.isEmpty()
            || value.length() > MAX_LENGTH) {
            throw new InvalidIdempotencyKeyException();
        }
    }
}
