package com.nursena.payflow.outbox.application.port.out;

import com.nursena.payflow.outbox.domain.model.OutboxEvent;

public interface OutboxMessagePublisherPort {

    void publish(
        OutboxEvent event
    );
}
