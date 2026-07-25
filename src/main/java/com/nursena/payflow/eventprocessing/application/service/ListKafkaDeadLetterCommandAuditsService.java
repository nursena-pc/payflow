package com.nursena.payflow.eventprocessing.application.service;

import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditPage;
import com.nursena.payflow.eventprocessing.application.port.in
    .ListKafkaDeadLetterCommandAuditsQuery;
import com.nursena.payflow.eventprocessing.application.port.in
    .ListKafkaDeadLetterCommandAuditsUseCase;
import com.nursena.payflow.eventprocessing.application.port.out
    .KafkaDeadLetterCommandAuditQueryPort;
import org.springframework.transaction.annotation.Transactional;

public class ListKafkaDeadLetterCommandAuditsService
    implements ListKafkaDeadLetterCommandAuditsUseCase {

    private final KafkaDeadLetterCommandAuditQueryPort
        queryPort;

    public ListKafkaDeadLetterCommandAuditsService(
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
    public KafkaDeadLetterCommandAuditPage
    listKafkaDeadLetterCommandAudits(
        ListKafkaDeadLetterCommandAuditsQuery query
    ) {
        ListKafkaDeadLetterCommandAuditsQuery
            validatedQuery =
            Objects.requireNonNull(
                query,
                "query must not be null"
            );

        return queryPort.findPage(
            validatedQuery.page(),
            validatedQuery.size(),
            validatedQuery.filter()
        );
    }
}
