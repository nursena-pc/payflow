package com.nursena.payflow.eventprocessing.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import com.nursena.payflow.eventprocessing.application.model.RecordKafkaDeadLetterCommand;
import com.nursena.payflow.eventprocessing.application.model.RecordKafkaDeadLetterResult;
import com.nursena.payflow.eventprocessing.application.port.in.RecordKafkaDeadLetterUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterRecordRepositoryPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.springframework.transaction.annotation.Transactional;

public class RecordKafkaDeadLetterService
    implements RecordKafkaDeadLetterUseCase {

    private final KafkaDeadLetterRecordRepositoryPort
        repository;

    private final Clock clock;

    private final Supplier<UUID> idSupplier;

    public RecordKafkaDeadLetterService(
        KafkaDeadLetterRecordRepositoryPort
            repository,
        Clock clock,
        Supplier<UUID> idSupplier
    ) {
        this.repository =
            Objects.requireNonNull(
                repository,
                "repository must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        this.idSupplier =
            Objects.requireNonNull(
                idSupplier,
                "idSupplier must not be null"
            );
    }

    @Override
    @Transactional
    public RecordKafkaDeadLetterResult record(
        RecordKafkaDeadLetterCommand command
    ) {
        Objects.requireNonNull(
            command,
            "command must not be null"
        );

        UUID recordId =
            Objects.requireNonNull(
                idSupplier.get(),
                "idSupplier must not return null"
            );

        KafkaDeadLetterRecord record =
            new KafkaDeadLetterRecord(
                recordId,
                command.deadLetterTopic(),
                command.deadLetterPartition(),
                command.deadLetterOffset(),
                command.originalTopic(),
                command.originalPartition(),
                command.originalOffset(),
                command.originalConsumerGroup(),
                command.recordKey(),
                command.payload(),
                command.exceptionType(),
                command.exceptionMessage(),
                KafkaDeadLetterRecordStatus.RECEIVED,
                0,
                currentTime(),
                null,
                null,
                null,
                null
            );

        boolean recorded =
            repository.tryRecord(record);

        if (!recorded) {
            return RecordKafkaDeadLetterResult
                .DUPLICATE;
        }

        return RecordKafkaDeadLetterResult
            .RECORDED;
    }

    private Instant currentTime() {
        return clock
            .instant()
            .truncatedTo(
                ChronoUnit.MICROS
            );
    }
}
