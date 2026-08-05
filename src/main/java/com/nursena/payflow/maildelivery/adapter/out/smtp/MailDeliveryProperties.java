package com.nursena.payflow.maildelivery.adapter.out.smtp;

import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.mail.delivery")
public record MailDeliveryProperties(
    String fromAddress,
    String fromName
) {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public MailDeliveryProperties {
        Objects.requireNonNull(fromAddress, "fromAddress must not be null");
        fromAddress = fromAddress.trim();
        if (fromAddress.length() > 320 || !EMAIL_PATTERN.matcher(fromAddress).matches()) {
            throw new IllegalArgumentException("fromAddress must be a valid email address");
        }
        if (fromName == null || fromName.isBlank() || fromName.length() > 100) {
            throw new IllegalArgumentException("fromName must be non-blank and at most 100 characters");
        }
        fromName = fromName.trim();
    }
}
