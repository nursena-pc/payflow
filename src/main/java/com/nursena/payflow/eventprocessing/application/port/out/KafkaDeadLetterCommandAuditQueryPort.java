package com.nursena.payflow.eventprocessing.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditFilter;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditPage;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditTimeline;

public interface KafkaDeadLetterCommandAuditQueryPort {

    KafkaDeadLetterCommandAuditPage findPage(
        int page,
        int size,
        KafkaDeadLetterCommandAuditFilter filter
    );

    Optional<KafkaDeadLetterCommandAuditTimeline>
    findTimelineByCommandId(
        UUID commandId
    );
}
