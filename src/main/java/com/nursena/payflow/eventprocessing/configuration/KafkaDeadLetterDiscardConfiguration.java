package com.nursena.payflow.eventprocessing.configuration;

import com.nursena.payflow.eventprocessing.application.port.in.DiscardKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterDiscardPort;
import com.nursena.payflow.eventprocessing.application.service.DiscardKafkaDeadLetterRecordService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class KafkaDeadLetterDiscardConfiguration {

    @Bean
    DiscardKafkaDeadLetterRecordUseCase
    discardKafkaDeadLetterRecordUseCase(
        KafkaDeadLetterDiscardPort discardPort
    ) {
        return new DiscardKafkaDeadLetterRecordService(
            discardPort
        );
    }
}
