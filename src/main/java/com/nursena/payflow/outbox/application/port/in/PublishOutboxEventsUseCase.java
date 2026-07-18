package com.nursena.payflow.outbox.application.port.in;

public interface PublishOutboxEventsUseCase {

    PublishOutboxEventsResult publishAvailable(
        PublishOutboxEventsCommand command
    );
}
