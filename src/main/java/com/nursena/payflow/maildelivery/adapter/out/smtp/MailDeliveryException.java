package com.nursena.payflow.maildelivery.adapter.out.smtp;

final class MailDeliveryException extends RuntimeException {

    MailDeliveryException(Throwable cause) {
        super("SMTP delivery failed.", cause);
    }
}
