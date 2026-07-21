package com.nursena.payflow.eventprocessing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.ProcessTransferCompletedEventCommand;
import com.nursena.payflow.eventprocessing.application.model.ProcessTransferCompletedEventResult;
import com.nursena.payflow.eventprocessing.application.port.out.ProcessedKafkaEventRepositoryPort;
import com.nursena.payflow.eventprocessing.application.port.out.TransferCompletedEventHandlerPort;
import com.nursena.payflow.eventprocessing.domain.model.ProcessedKafkaEvent;
import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessTransferCompletedEventServiceTest {

    private static final String CONSUMER_NAME =
        "transfer-completed-notification";

    private static final Instant PROCESSED_AT =
        Instant.parse(
            "2026-07-20T21:00:00.123456Z"
        );

    @Mock
    private ProcessedKafkaEventRepositoryPort
        processedEventRepository;

    @Mock
    private TransferCompletedEventHandlerPort
        eventHandler;

    private ProcessTransferCompletedEventService
        service;

    @BeforeEach
    void setUp() {
        service =
            new ProcessTransferCompletedEventService(
                CONSUMER_NAME,
                processedEventRepository,
                eventHandler,
                Clock.fixed(
                    PROCESSED_AT,
                    ZoneOffset.UTC
                )
            );
    }

    @Test
    void shouldProcessFirstDelivery() {
        when(
            processedEventRepository.tryRecord(
                any(ProcessedKafkaEvent.class)
            )
        )
            .thenReturn(true);

        ProcessTransferCompletedEventResult result =
            service.process(command());

        assertThat(result)
            .isEqualTo(
                ProcessTransferCompletedEventResult
                    .PROCESSED
            );

        ArgumentCaptor<ProcessedKafkaEvent> captor =
            ArgumentCaptor.forClass(
                ProcessedKafkaEvent.class
            );

        verify(processedEventRepository)
            .tryRecord(captor.capture());

        ProcessedKafkaEvent recordedEvent =
            captor.getValue();

        assertThat(recordedEvent.consumerName())
            .isEqualTo(CONSUMER_NAME);

        assertThat(recordedEvent.eventId())
            .isEqualTo(event().eventId());

        assertThat(recordedEvent.topic())
            .isEqualTo(
                TransferCompletedEvent.TYPE
            );

        assertThat(recordedEvent.partitionNumber())
            .isZero();

        assertThat(recordedEvent.recordOffset())
            .isEqualTo(25L);

        assertThat(recordedEvent.processedAt())
            .isEqualTo(PROCESSED_AT);

        verify(eventHandler)
            .handle(event());
    }

    @Test
    void shouldSkipHandlerForDuplicateDelivery() {
        when(
            processedEventRepository.tryRecord(
                any(ProcessedKafkaEvent.class)
            )
        )
            .thenReturn(false);

        ProcessTransferCompletedEventResult result =
            service.process(command());

        assertThat(result)
            .isEqualTo(
                ProcessTransferCompletedEventResult
                    .DUPLICATE
            );

        verify(
            eventHandler,
            never()
        )
            .handle(any());
    }

    @Test
    void shouldPropagateHandlerFailure() {
        when(
            processedEventRepository.tryRecord(
                any(ProcessedKafkaEvent.class)
            )
        )
            .thenReturn(true);

        org.mockito.Mockito
            .doThrow(
                new IllegalStateException(
                    "handler failure"
                )
            )
            .when(eventHandler)
            .handle(event());

        assertThatThrownBy(
            () -> service.process(command())
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "handler failure"
            );
    }

    private static
    ProcessTransferCompletedEventCommand command() {
        return new ProcessTransferCompletedEventCommand(
            event(),
            TransferCompletedEvent.TYPE,
            0,
            25L
        );
    }

    private static TransferCompletedEvent event() {
        return new TransferCompletedEvent(
            UUID.fromString(
                "50000000-0000-0000-0000-000000000601"
            ),
            TransferCompletedEvent.TYPE,
            TransferCompletedEvent.VERSION,
            Instant.parse(
                "2026-07-20T20:00:00Z"
            ),
            UUID.fromString(
                "60000000-0000-0000-0000-000000000601"
            ),
            UUID.fromString(
                "70000000-0000-0000-0000-000000000601"
            ),
            UUID.fromString(
                "70000000-0000-0000-0000-000000000602"
            ),
            "125.50",
            "TRY"
        );
    }
}
