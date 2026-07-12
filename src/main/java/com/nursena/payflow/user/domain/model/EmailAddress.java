package com.nursena.payflow.user.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import com.nursena.payflow.user.domain.exception.InvalidEmailException;

public record EmailAddress(String value) {

    private static final int MAX_LENGTH = 320;

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public EmailAddress {
        Objects.requireNonNull(value, "email must not be null");

        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);

        if (normalizedValue.isBlank()
            || normalizedValue.length() > MAX_LENGTH
            || !EMAIL_PATTERN.matcher(normalizedValue).matches()) {
            throw new InvalidEmailException();
        }

        value = normalizedValue;
    }

    public static EmailAddress of(String value) {
        return new EmailAddress(value);
    }
}
