package com.nursena.payflow.eventprocessing.configuration;

import java.time.Clock;

import com.nursena.payflow.eventprocessing.adapter.out.kafka.KafkaDeadLetterReplayPublisherAdapter;
import com.nursena.payflow.eventprocessing.application.port.in.ClaimKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.in.ReplayKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayLifecyclePort;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayPublisherPort;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayRepositoryPort;
import com.nursena.payflow.eventprocessing.application.service.ClaimKafkaDeadLetterRecordService;
import com.nursena.payflow.eventprocessing.application.service.ReplayKafkaDeadLetterRecordService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;


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
    @Bean
    KafkaDeadLetterReplayPublisherPort
    kafkaDeadLetterReplayPublisherPort(
        KafkaTemplate<String, String>
            kafkaTemplate,
        KafkaDeadLetterReplayProperties
            properties
    ) {
        return new
            KafkaDeadLetterReplayPublisherAdapter(
            kafkaTemplate,
            properties.sendTimeout()
        );
    }
    @Bean
    ReplayKafkaDeadLetterRecordUseCase
    replayKafkaDeadLetterRecordUseCase(
        ClaimKafkaDeadLetterRecordUseCase
            claimUseCase,
        KafkaDeadLetterReplayPublisherPort
            publisherPort,
        KafkaDeadLetterReplayLifecyclePort
            lifecyclePort,
        Clock clock
    ) {
        return new
            ReplayKafkaDeadLetterRecordService(
            claimUseCase,
            publisherPort,
            lifecyclePort,
            clock
        );
    }
}
