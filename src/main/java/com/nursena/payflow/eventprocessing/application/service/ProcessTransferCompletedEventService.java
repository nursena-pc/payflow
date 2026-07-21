package com.nursena.payflow.eventprocessing.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.model.ProcessTransferCompletedEventCommand;
import com.nursena.payflow.eventprocessing.application.model.ProcessTransferCompletedEventResult;
import com.nursena.payflow.eventprocessing.application.port.in.ProcessTransferCompletedEventUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.ProcessedKafkaEventRepositoryPort;
import com.nursena.payflow.eventprocessing.application.port.out.TransferCompletedEventHandlerPort;
import com.nursena.payflow.eventprocessing.domain.model.ProcessedKafkaEvent;
import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;
import org.springframework.transaction.annotation.Transactional;

public class ProcessTransferCompletedEventService
    implements ProcessTransferCompletedEventUseCase {

    private static final int MAX_CONSUMER_NAME_LENGTH =
        200;

    private final String consumerName;

    private final ProcessedKafkaEventRepositoryPort
        processedEventRepository;

    private final TransferCompletedEventHandlerPort
        eventHandler;

    private final Clock clock;

    public ProcessTransferCompletedEventService(
        String consumerName,
        ProcessedKafkaEventRepositoryPort
            processedEventRepository,
        TransferCompletedEventHandlerPort eventHandler,
        Clock clock
    ) {
        this.consumerName =
            validateConsumerName(consumerName);

        this.processedEventRepository =
            Objects.requireNonNull(
                processedEventRepository,
                "processedEventRepository "
                    + "must not be null"
            );

        this.eventHandler =
            Objects.requireNonNull(
                eventHandler,
                "eventHandler must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Override
    @Transactional
    public ProcessTransferCompletedEventResult process(
        ProcessTransferCompletedEventCommand command
    ) {
        Objects.requireNonNull(
            command,
            "command must not be null"
        );

        TransferCompletedEvent event =
            command.event();

        ProcessedKafkaEvent processedEvent =
            new ProcessedKafkaEvent(
                consumerName,
                event.eventId(),
                event.eventType(),
                event.eventVersion(),
                command.topic(),
                command.partitionNumber(),
                command.recordOffset(),
                currentTime()
            );

        boolean recorded =
            processedEventRepository.tryRecord(
                processedEvent
            );

        if (!recorded) {
            return ProcessTransferCompletedEventResult
                .DUPLICATE;
        }

        eventHandler.handle(event);

        return ProcessTransferCompletedEventResult
            .PROCESSED;
    }

    private Instant currentTime() {
        return clock
            .instant()
            .truncatedTo(
                ChronoUnit.MICROS
            );
    }

    private static String validateConsumerName(
        String consumerName
    ) {
        if (consumerName == null
            || consumerName.isBlank()) {

            throw new IllegalArgumentException(
                "consumerName must not be blank."
            );
        }

        if (consumerName.length()
            > MAX_CONSUMER_NAME_LENGTH) {

            throw new IllegalArgumentException(
                "consumerName must not exceed "
                    + MAX_CONSUMER_NAME_LENGTH
                    + " characters."
            );
        }

        return consumerName;
    }
}
