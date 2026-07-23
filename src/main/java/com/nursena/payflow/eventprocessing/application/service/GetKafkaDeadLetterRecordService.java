package com.nursena.payflow.eventprocessing.application.service;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterRecordDetails;
import com.nursena.payflow.eventprocessing.application.port.in
    .GetKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.out
    .KafkaDeadLetterQueryPort;
import com.nursena.payflow.eventprocessing.domain.exception
    .KafkaDeadLetterRecordNotFoundException;
import org.springframework.transaction.annotation.Transactional;

public class GetKafkaDeadLetterRecordService
    implements GetKafkaDeadLetterRecordUseCase {

    private final KafkaDeadLetterQueryPort queryPort;

    public GetKafkaDeadLetterRecordService(
        KafkaDeadLetterQueryPort queryPort
    ) {
        this.queryPort =
            Objects.requireNonNull(
                queryPort,
                "queryPort must not be null"
            );
    }

    @Override
    @Transactional(readOnly = true)
    public KafkaDeadLetterRecordDetails
    getKafkaDeadLetterRecord(
        UUID recordId
    ) {
        UUID validatedRecordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );

        return queryPort
            .findById(validatedRecordId)
            .orElseThrow(
                () ->
                    new KafkaDeadLetterRecordNotFoundException(
                        validatedRecordId
                    )
            );
    }
}
