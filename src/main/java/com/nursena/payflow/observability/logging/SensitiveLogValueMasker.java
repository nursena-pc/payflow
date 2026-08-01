package com.nursena.payflow.observability.logging;

import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonStreamContext;

import net.logstash.logback.mask.ValueMasker;

public final class SensitiveLogValueMasker
    implements ValueMasker {

    public static final String MASK =
        "[REDACTED]";

    private static final String SENSITIVE_KEY =
        "authorization"
            + "|password"
            + "|passphrase"
            + "|refresh[_-]?token"
            + "|access[_-]?token"
            + "|client[_-]?secret"
            + "|api[_-]?key"
            + "|private[_-]?key"
            + "|secret"
            + "|token";

    private static final Pattern SECRET_ASSIGNMENT =
        Pattern.compile(
            "(?i)([\"']?(?:"
                + SENSITIVE_KEY
                + ")[\"']?\\s*[:=]\\s*)"
                + "(?:Bearer\\s+[^\\s,;]+"
                + "|\"[^\"]*\""
                + "|'[^']*'"
                + "|[^\\s,;]+)"
        );

    private static final Pattern BEARER_CREDENTIAL =
        Pattern.compile(
            "(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+"
        );

    private static final Pattern JWT =
        Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]{5,}"
                + "\\.[A-Za-z0-9_-]{5,}"
                + "\\.[A-Za-z0-9_-]{5,}\\b"
        );

    @Override
    public Object mask(
        JsonStreamContext context,
        Object value
    ) {
        if (!(value instanceof CharSequence characters)) {
            return null;
        }

        String original =
            characters.toString();

        String redacted =
            redact(original);

        if (original.equals(redacted)) {
            return null;
        }

        return redacted;
    }

    public static String redact(
        String value
    ) {
        Objects.requireNonNull(
            value,
            "log value must not be null"
        );

        String redacted =
            SECRET_ASSIGNMENT
                .matcher(value)
                .replaceAll(
                    "$1" + MASK
                );

        redacted =
            BEARER_CREDENTIAL
                .matcher(redacted)
                .replaceAll(
                    "Bearer " + MASK
                );

        return JWT
            .matcher(redacted)
            .replaceAll(MASK);
    }
}