package com.nursena.payflow.eventprocessing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.RecordKafkaDeadLetterCommand;
import com.nursena.payflow.eventprocessing.application.model.RecordKafkaDeadLetterResult;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterRecordRepositoryPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordKafkaDeadLetterServiceTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001201"
        );

    private static final Instant RECEIVED_AT =
        Instant.parse(
            "2026-07-21T18:00:00.123456Z"
        );

    @Mock
    private KafkaDeadLetterRecordRepositoryPort
        repository;

    private RecordKafkaDeadLetterService service;

    @BeforeEach
    void setUp() {
        service =
            new RecordKafkaDeadLetterService(
                repository,
                Clock.fixed(
                    RECEIVED_AT,
                    ZoneOffset.UTC
                ),
                () -> RECORD_ID
            );
    }

    @Test
    void shouldRecordFirstDeadLetterDelivery() {
        when(
            repository.tryRecord(
                any(KafkaDeadLetterRecord.class)
            )
        )
            .thenReturn(true);

        RecordKafkaDeadLetterResult result =
            service.record(command());

        assertThat(result)
            .isEqualTo(
                RecordKafkaDeadLetterResult.RECORDED
            );

        ArgumentCaptor<KafkaDeadLetterRecord>
            captor =
            ArgumentCaptor.forClass(
                KafkaDeadLetterRecord.class
            );

        verify(repository)
            .tryRecord(captor.capture());

        KafkaDeadLetterRecord record =
            captor.getValue();

        assertThat(record.id())
            .isEqualTo(RECORD_ID);

        assertThat(record.deadLetterTopic())
            .isEqualTo(
                "wallet.transfer.completed.dlt"
            );

        assertThat(record.deadLetterPartition())
            .isEqualTo(2);

        assertThat(record.deadLetterOffset())
            .isEqualTo(25L);

        assertThat(record.originalTopic())
            .isEqualTo(
                "wallet.transfer.completed"
            );

        assertThat(record.originalOffset())
            .isEqualTo(42L);

        assertThat(record.recordKey())
            .isNull();

        assertThat(record.payload())
            .isNull();

        assertThat(record.status())
            .isEqualTo(
                KafkaDeadLetterRecordStatus.RECEIVED
            );

        assertThat(record.replayCount())
            .isZero();

        assertThat(record.receivedAt())
            .isEqualTo(RECEIVED_AT);
    }

    @Test
    void shouldReturnDuplicateForExistingLocation() {
        when(
            repository.tryRecord(
                any(KafkaDeadLetterRecord.class)
            )
        )
            .thenReturn(false);

        RecordKafkaDeadLetterResult result =
            service.record(command());

        assertThat(result)
            .isEqualTo(
                RecordKafkaDeadLetterResult.DUPLICATE
            );
    }

    @Test
    void shouldRejectNullCommand() {
        assertThatThrownBy(
            () -> service.record(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "command must not be null"
            );
    }

    @Test
    void shouldRejectNullGeneratedIdentifier() {
        RecordKafkaDeadLetterService
            invalidService =
            new RecordKafkaDeadLetterService(
                repository,
                Clock.fixed(
                    RECEIVED_AT,
                    ZoneOffset.UTC
                ),
                () -> null
            );

        assertThatThrownBy(
            () -> invalidService.record(command())
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "idSupplier must not return null"
            );
    }

    private static RecordKafkaDeadLetterCommand
    command() {
        return new RecordKafkaDeadLetterCommand(
            "wallet.transfer.completed.dlt",
            2,
            25L,
            "wallet.transfer.completed",
            1,
            42L,
            "payflow-transfer-completed-audit-v1",
            null,
            null,
            "java.lang.IllegalStateException",
            "Temporary processing failure."
        );
    }
}
