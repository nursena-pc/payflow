package com.nursena.payflow.maildelivery.adapter.out.security;

final class MailContentProtectionException extends RuntimeException {

    MailContentProtectionException() {
        super("Protected mail content is invalid.");
    }

    MailContentProtectionException(Throwable cause) {
        super("Mail content protection failed.", cause);
    }
}
