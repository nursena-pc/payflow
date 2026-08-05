package com.nursena.payflow.maildelivery.application.port.out;

import java.time.Instant;

import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;

public interface MailOutboxEnqueuePort {

    void replaceUnresolved(
        MailOutboxMessage message,
        Instant replacedAt
    );
}
