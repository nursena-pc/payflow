package com.nursena.payflow.user.adapter.out.security;

import java.util.Objects;
import java.util.regex.Pattern;

record JwtKeyId(String value) {

    private static final Pattern ALLOWED_VALUE =
        Pattern.compile("[A-Za-z0-9_-]{1,64}");

    JwtKeyId {
        Objects.requireNonNull(
            value,
            "value must not be null"
        );

        if (!ALLOWED_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "JWT key ID must contain 1 to 64 "
                    + "letters, digits, underscores, or hyphens"
            );
        }
    }

    static JwtKeyId of(String value) {
        return new JwtKeyId(value);
    }
}
