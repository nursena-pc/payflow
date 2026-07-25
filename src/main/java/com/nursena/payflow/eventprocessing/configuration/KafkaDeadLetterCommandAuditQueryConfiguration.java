package com.nursena.payflow.eventprocessing.configuration;

import com.nursena.payflow.eventprocessing.application.port.in
    .GetKafkaDeadLetterCommandAuditTimelineUseCase;
import com.nursena.payflow.eventprocessing.application.port.in
    .ListKafkaDeadLetterCommandAuditsUseCase;
import com.nursena.payflow.eventprocessing.application.port.out
    .KafkaDeadLetterCommandAuditQueryPort;
import com.nursena.payflow.eventprocessing.application.service
    .GetKafkaDeadLetterCommandAuditTimelineService;
import com.nursena.payflow.eventprocessing.application.service
    .ListKafkaDeadLetterCommandAuditsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class KafkaDeadLetterCommandAuditQueryConfiguration {

    @Bean
    ListKafkaDeadLetterCommandAuditsUseCase
    listKafkaDeadLetterCommandAuditsUseCase(
        KafkaDeadLetterCommandAuditQueryPort queryPort
    ) {
        return new ListKafkaDeadLetterCommandAuditsService(
            queryPort
        );
    }

    @Bean
    GetKafkaDeadLetterCommandAuditTimelineUseCase
    getKafkaDeadLetterCommandAuditTimelineUseCase(
        KafkaDeadLetterCommandAuditQueryPort queryPort
    ) {
        return new GetKafkaDeadLetterCommandAuditTimelineService(
            queryPort
        );
    }
}
