package com.nursena.payflow.eventprocessing.application.port.in;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditPage;

public interface ListKafkaDeadLetterCommandAuditsUseCase {

    KafkaDeadLetterCommandAuditPage
    listKafkaDeadLetterCommandAudits(
        ListKafkaDeadLetterCommandAuditsQuery query
    );
}
