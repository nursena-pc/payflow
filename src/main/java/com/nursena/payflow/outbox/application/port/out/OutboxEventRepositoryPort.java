package com.nursena.payflow.outbox.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.outbox.domain.model.OutboxEvent;

public interface OutboxEventRepositoryPort {

    OutboxEvent save(
        OutboxEvent event
    );

    Optional<OutboxEvent> findById(
        UUID eventId
    );
}
