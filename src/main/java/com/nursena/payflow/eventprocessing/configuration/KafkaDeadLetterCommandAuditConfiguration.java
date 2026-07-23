package com.nursena.payflow.eventprocessing.configuration;

import java.time.Clock;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.port.in.DiscardKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.in.OperatorDiscardKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.in.OperatorReplayKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.in.ReplayKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterCommandAuditPort;
import com.nursena.payflow.eventprocessing.application.service.OperatorDiscardKafkaDeadLetterRecordService;
import com.nursena.payflow.eventprocessing.application.service.OperatorReplayKafkaDeadLetterRecordService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class KafkaDeadLetterCommandAuditConfiguration {

    @Bean
    OperatorReplayKafkaDeadLetterRecordUseCase
    operatorReplayKafkaDeadLetterRecordUseCase(
        ReplayKafkaDeadLetterRecordUseCase replayUseCase,
        KafkaDeadLetterCommandAuditPort auditPort,
        Clock clock
    ) {
        return new
            OperatorReplayKafkaDeadLetterRecordService(
            replayUseCase,
            auditPort,
            clock,
            UUID::randomUUID
        );
    }

    @Bean
    OperatorDiscardKafkaDeadLetterRecordUseCase
    operatorDiscardKafkaDeadLetterRecordUseCase(
        DiscardKafkaDeadLetterRecordUseCase
            discardUseCase,
        KafkaDeadLetterCommandAuditPort auditPort,
        Clock clock
    ) {
        return new
            OperatorDiscardKafkaDeadLetterRecordService(
            discardUseCase,
            auditPort,
            clock,
            UUID::randomUUID
        );
    }
}
