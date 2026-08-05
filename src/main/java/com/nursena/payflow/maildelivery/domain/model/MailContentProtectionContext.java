package com.nursena.payflow.maildelivery.domain.model;

import java.util.Objects;
import java.util.UUID;

public record MailContentProtectionContext(
    UUID messageId,
    UUID userId,
    MailOutboxPurpose purpose,
    String recipient,
    String subject
) {

    private static final int MAX_RECIPIENT_LENGTH = 320;
    private static final int MAX_SUBJECT_LENGTH = 200;

    public MailContentProtectionContext {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        recipient = requireBounded(
            recipient,
            "recipient",
            MAX_RECIPIENT_LENGTH
        );
        subject = requireBounded(subject, "subject", MAX_SUBJECT_LENGTH);
    }

    private static String requireBounded(
        String value,
        String field,
        int maxLength
    ) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                field + " must be non-blank and at most "
                    + maxLength
                    + " characters"
            );
        }
        return value;
    }

    @Override
    public String toString() {
        return "MailContentProtectionContext[messageId="
            + messageId
            + ", userId="
            + userId
            + ", purpose="
            + purpose
            + ", sensitiveMetadata=redacted]";
    }
}
