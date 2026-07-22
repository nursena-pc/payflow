package com.nursena.payflow.eventprocessing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordDetails;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordSummary;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterQueryPort;
import com.nursena.payflow.eventprocessing.domain.exception.KafkaDeadLetterRecordNotFoundException;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetKafkaDeadLetterRecordServiceTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "637398d5-0a02-4d10-a9af-c783ef92778b"
        );

    @Mock
    private KafkaDeadLetterQueryPort queryPort;

    private GetKafkaDeadLetterRecordService service;

    @BeforeEach
    void setUp() {
        service =
            new GetKafkaDeadLetterRecordService(
                queryPort
            );
    }

    @Test
    void shouldReturnExistingRecord() {
        KafkaDeadLetterRecordDetails details =
            createDetails();

        when(
            queryPort.findById(RECORD_ID)
        ).thenReturn(
            Optional.of(details)
        );

        KafkaDeadLetterRecordDetails result =
            service.getKafkaDeadLetterRecord(
                RECORD_ID
            );

        assertThat(result).isSameAs(details);

        verify(queryPort).findById(RECORD_ID);
    }

    @Test
    void shouldThrowWhenRecordDoesNotExist() {
        when(
            queryPort.findById(RECORD_ID)
        ).thenReturn(
            Optional.empty()
        );

        KafkaDeadLetterRecordNotFoundException
            exception =
            catchThrowableOfType(
                () ->
                    service
                        .getKafkaDeadLetterRecord(
                            RECORD_ID
                        ),
                KafkaDeadLetterRecordNotFoundException
                    .class
            );

        assertThat(exception.getCode())
            .isEqualTo(
                KafkaDeadLetterRecordNotFoundException
                    .CODE
            );

        assertThat(exception.getRecordId())
            .isEqualTo(RECORD_ID);

        assertThat(exception)
            .hasMessage(
                "Kafka dead-letter record "
                    + "was not found."
            );

        verify(queryPort).findById(RECORD_ID);
    }

    @Test
    void shouldRejectNullRecordIdentifier() {
        assertThatThrownBy(
            () ->
                service.getKafkaDeadLetterRecord(
                    null
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "recordId must not be null"
            );

        verifyNoInteractions(queryPort);
    }

    private static KafkaDeadLetterRecordDetails
    createDetails() {
        Instant receivedAt =
            Instant.parse(
                "2026-07-22T12:00:00Z"
            );

        KafkaDeadLetterRecordSummary summary =
            new KafkaDeadLetterRecordSummary(
                RECORD_ID,
                KafkaDeadLetterRecordStatus
                    .REPLAY_FAILED,
                "wallet.transfer.completed.dlt",
                0,
                42L,
                "wallet.transfer.completed",
                0,
                41L,
                "payflow-transfer-consumer",
                "java.lang.IllegalStateException",
                1,
                0,
                receivedAt,
                receivedAt.plusSeconds(30),
                RECORD_ID,
                true
            );

        return new KafkaDeadLetterRecordDetails(
            summary,
            "Transfer processing failed.",
            "Replay publication failed.",
            null
        );
    }
}
