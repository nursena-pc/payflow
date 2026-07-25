package com.nursena.payflow.eventprocessing.application.port.in;

import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditTimeline;

public interface
GetKafkaDeadLetterCommandAuditTimelineUseCase {

    KafkaDeadLetterCommandAuditTimeline
    getKafkaDeadLetterCommandAuditTimeline(
        UUID commandId
    );
}
