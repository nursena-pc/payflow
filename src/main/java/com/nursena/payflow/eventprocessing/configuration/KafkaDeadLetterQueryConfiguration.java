package com.nursena.payflow.eventprocessing.configuration;

import com.nursena.payflow.eventprocessing.application.port.in.GetKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.in.ListKafkaDeadLetterRecordsUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterQueryPort;
import com.nursena.payflow.eventprocessing.application.service.GetKafkaDeadLetterRecordService;
import com.nursena.payflow.eventprocessing.application.service.ListKafkaDeadLetterRecordsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class KafkaDeadLetterQueryConfiguration {

    @Bean
    ListKafkaDeadLetterRecordsUseCase
    listKafkaDeadLetterRecordsUseCase(
        KafkaDeadLetterQueryPort queryPort
    ) {
        return new ListKafkaDeadLetterRecordsService(
            queryPort
        );
    }

    @Bean
    GetKafkaDeadLetterRecordUseCase
    getKafkaDeadLetterRecordUseCase(
        KafkaDeadLetterQueryPort queryPort
    ) {
        return new GetKafkaDeadLetterRecordService(
            queryPort
        );
    }
}
