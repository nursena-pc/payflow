package com.nursena.payflow.eventprocessing.application.service;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditTimeline;
import com.nursena.payflow.eventprocessing.application.port.in
    .GetKafkaDeadLetterCommandAuditTimelineUseCase;
import com.nursena.payflow.eventprocessing.application.port.out
    .KafkaDeadLetterCommandAuditQueryPort;
import com.nursena.payflow.eventprocessing.domain.exception
    .KafkaDeadLetterCommandAuditTimelineNotFoundException;
import org.springframework.transaction.annotation.Transactional;

public class GetKafkaDeadLetterCommandAuditTimelineService
    implements GetKafkaDeadLetterCommandAuditTimelineUseCase {

    private final KafkaDeadLetterCommandAuditQueryPort
        queryPort;

    public GetKafkaDeadLetterCommandAuditTimelineService(
        KafkaDeadLetterCommandAuditQueryPort queryPort
    ) {
        this.queryPort =
            Objects.requireNonNull(
                queryPort,
                "queryPort must not be null"
            );
    }

    @Override
    @Transactional(readOnly = true)
    public KafkaDeadLetterCommandAuditTimeline
    getKafkaDeadLetterCommandAuditTimeline(
        UUID commandId
    ) {
        UUID validatedCommandId =
            Objects.requireNonNull(
                commandId,
                "commandId must not be null"
            );

        return queryPort
            .findTimelineByCommandId(
                validatedCommandId
            )
            .orElseThrow(
                () ->
                    new KafkaDeadLetterCommandAuditTimelineNotFoundException(
                        validatedCommandId
                    )
            );
    }
}
