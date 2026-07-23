package com.nursena.payflow.eventprocessing.application.port.out;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAudit;

public interface KafkaDeadLetterCommandAuditPort {

    void append(
        KafkaDeadLetterCommandAudit audit
    );
}
