package com.nursena.payflow.eventprocessing.application.service;

import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterRecordPage;
import com.nursena.payflow.eventprocessing.application.port.in
    .ListKafkaDeadLetterRecordsQuery;
import com.nursena.payflow.eventprocessing.application.port.in
    .ListKafkaDeadLetterRecordsUseCase;
import com.nursena.payflow.eventprocessing.application.port.out
    .KafkaDeadLetterQueryPort;
import org.springframework.transaction.annotation.Transactional;

public class ListKafkaDeadLetterRecordsService
    implements ListKafkaDeadLetterRecordsUseCase {

    private final KafkaDeadLetterQueryPort queryPort;

    public ListKafkaDeadLetterRecordsService(
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
    public KafkaDeadLetterRecordPage
    listKafkaDeadLetterRecords(
        ListKafkaDeadLetterRecordsQuery query
    ) {
        ListKafkaDeadLetterRecordsQuery
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
