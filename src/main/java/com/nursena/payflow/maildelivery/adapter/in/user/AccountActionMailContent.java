package com.nursena.payflow.maildelivery.adapter.in.user;

import java.util.Objects;

record AccountActionMailContent(
    String subject,
    String sensitiveBody
) {

    AccountActionMailContent {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(sensitiveBody, "sensitiveBody must not be null");
        if (subject.isBlank() || sensitiveBody.isBlank()) {
            throw new IllegalArgumentException("mail content must not be blank");
        }
    }

    @Override
    public String toString() {
        return "AccountActionMailContent[redacted]";
    }
}
