package com.nursena.payflow.eventprocessing.configuration;

import java.time.Clock;

import com.nursena.payflow.eventprocessing.application.port.in.ClaimKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayRepositoryPort;
import com.nursena.payflow.eventprocessing.application.service.ClaimKafkaDeadLetterRecordService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    KafkaDeadLetterReplayProperties.class
)
public class KafkaDeadLetterReplayConfiguration {

    @Bean
    ClaimKafkaDeadLetterRecordUseCase
    claimKafkaDeadLetterRecordUseCase(
        KafkaDeadLetterReplayRepositoryPort
            repository,
        KafkaDeadLetterReplayProperties
            properties,
        Clock clock
    ) {
        return new
            ClaimKafkaDeadLetterRecordService(
            repository,
            properties.workerId(),
            properties.leaseDuration(),
            properties.maxAttempts(),
            clock
        );
    }
}
