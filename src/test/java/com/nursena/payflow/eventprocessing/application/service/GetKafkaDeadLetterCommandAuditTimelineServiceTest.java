package com.nursena.payflow.eventprocessing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAudit;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditTimeline;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandType;
import com.nursena.payflow.eventprocessing.application.port.out
    .KafkaDeadLetterCommandAuditQueryPort;
import com.nursena.payflow.eventprocessing.domain.exception
    .KafkaDeadLetterCommandAuditTimelineNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetKafkaDeadLetterCommandAuditTimelineServiceTest {

    private static final UUID COMMAND_ID =
        UUID.fromString(
            "7719a09d-b79a-482e-8f38-69ebd8f7a504"
        );

    private static final UUID OPERATOR_ID =
        UUID.fromString(
            "e73cc614-07f3-432e-8533-1f682b84fac5"
        );

    private static final UUID RECORD_ID =
        UUID.fromString(
            "22ce3dc4-23f0-42c3-b8b1-f7b6f5d82795"
        );

    private static final UUID AUDIT_ID =
        UUID.fromString(
            "8891cded-87ad-48f3-8c73-7ef63ac0b6cb"
        );

    @Mock
    private KafkaDeadLetterCommandAuditQueryPort
        queryPort;

    private GetKafkaDeadLetterCommandAuditTimelineService
        service;

    @BeforeEach
    void setUp() {
        service =
            new GetKafkaDeadLetterCommandAuditTimelineService(
                queryPort
            );
    }

    @Test
    void shouldReturnExistingTimeline() {
        KafkaDeadLetterCommandAuditTimeline timeline =
            createTimeline();

        when(
            queryPort.findTimelineByCommandId(
                COMMAND_ID
            )
        ).thenReturn(
            Optional.of(timeline)
        );

        KafkaDeadLetterCommandAuditTimeline result =
            service.getKafkaDeadLetterCommandAuditTimeline(
                COMMAND_ID
            );

        assertThat(result).isSameAs(timeline);

        verify(queryPort).findTimelineByCommandId(
            COMMAND_ID
        );
    }

    @Test
    void shouldThrowWhenTimelineDoesNotExist() {
        when(
            queryPort.findTimelineByCommandId(
                COMMAND_ID
            )
        ).thenReturn(
            Optional.empty()
        );

        KafkaDeadLetterCommandAuditTimelineNotFoundException
            exception =
            catchThrowableOfType(
                () ->
                    service
                        .getKafkaDeadLetterCommandAuditTimeline(
                            COMMAND_ID
                        ),
                KafkaDeadLetterCommandAuditTimelineNotFoundException
                    .class
            );

        assertThat(exception.getCode())
            .isEqualTo(
                KafkaDeadLetterCommandAuditTimelineNotFoundException
                    .CODE
            );
        assertThat(exception.getCommandId())
            .isEqualTo(COMMAND_ID);
        assertThat(exception)
            .hasMessage(
                "Kafka dead-letter command audit timeline "
                    + "was not found."
            );

        verify(queryPort).findTimelineByCommandId(
            COMMAND_ID
        );
    }

    @Test
    void shouldRejectNullCommandIdentifier() {
        assertThatThrownBy(
            () ->
                service
                    .getKafkaDeadLetterCommandAuditTimeline(
                        null
                    )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "commandId must not be null"
            );

        verifyNoInteractions(queryPort);
    }

    @Test
    void shouldRejectNullQueryPort() {
        assertThatThrownBy(
            () ->
                new GetKafkaDeadLetterCommandAuditTimelineService(
                    null
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "queryPort must not be null"
            );
    }

    private static KafkaDeadLetterCommandAuditTimeline
    createTimeline() {
        KafkaDeadLetterCommandAudit attempted =
            KafkaDeadLetterCommandAudit.attempted(
                AUDIT_ID,
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.REPLAY,
                Instant.parse(
                    "2026-07-25T10:00:00Z"
                )
            );

        return new KafkaDeadLetterCommandAuditTimeline(
            COMMAND_ID,
            List.of(attempted)
        );
    }
}
