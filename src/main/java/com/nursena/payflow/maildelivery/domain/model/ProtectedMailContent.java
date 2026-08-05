package com.nursena.payflow.maildelivery.domain.model;

import java.util.Arrays;
import java.util.Objects;

public final class ProtectedMailContent {

    private static final int MAX_LENGTH = 32768;

    private final byte[] value;

    private ProtectedMailContent(byte[] value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.length == 0 || value.length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "protected mail content length is invalid"
            );
        }
        this.value = value.clone();
    }

    public static ProtectedMailContent of(byte[] value) {
        return new ProtectedMailContent(value);
    }

    public byte[] value() {
        return value.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ProtectedMailContent content
            && Arrays.equals(value, content.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "ProtectedMailContent[redacted]";
    }
}
