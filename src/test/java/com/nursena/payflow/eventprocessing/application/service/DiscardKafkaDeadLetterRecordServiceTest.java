package com.nursena.payflow.eventprocessing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterDiscardPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscardKafkaDeadLetterRecordServiceTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002202"
        );

    private static final DiscardKafkaDeadLetterRecordCommand
        COMMAND =
        new DiscardKafkaDeadLetterRecordCommand(
            RECORD_ID
        );

    @Mock
    private KafkaDeadLetterDiscardPort discardPort;

    private DiscardKafkaDeadLetterRecordService
        service;

    @BeforeEach
    void setUp() {
        service =
            new DiscardKafkaDeadLetterRecordService(
                discardPort
            );
    }

    @Test
    void shouldReturnDiscardedResult() {
        assertPortResultIsPassedThrough(
            DiscardKafkaDeadLetterRecordResult
                .discarded()
        );
    }

    @Test
    void shouldReturnAlreadyDiscardedResult() {
        assertPortResultIsPassedThrough(
            DiscardKafkaDeadLetterRecordResult
                .alreadyDiscarded()
        );
    }

    @Test
    void shouldReturnNotFoundResult() {
        assertPortResultIsPassedThrough(
            DiscardKafkaDeadLetterRecordResult
                .notFound()
        );
    }

    @Test
    void shouldReturnNotDiscardableResult() {
        assertPortResultIsPassedThrough(
            DiscardKafkaDeadLetterRecordResult
                .notDiscardable()
        );
    }

    @Test
    void shouldRejectNullPortResult() {
        when(
            discardPort.discard(RECORD_ID)
        ).thenReturn(null);

        assertThatThrownBy(
            () -> service.discard(COMMAND)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "discard result must not be null"
            );
    }

    @Test
    void shouldRequireCommand() {
        assertThatThrownBy(
            () -> service.discard(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "command must not be null"
            );

        verifyNoInteractions(discardPort);
    }

    @Test
    void shouldRequireDiscardPort() {
        assertThatThrownBy(
            () ->
                new DiscardKafkaDeadLetterRecordService(
                    null
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "discardPort must not be null"
            );
    }

    private void assertPortResultIsPassedThrough(
        DiscardKafkaDeadLetterRecordResult
            portResult
    ) {
        when(
            discardPort.discard(RECORD_ID)
        ).thenReturn(portResult);

        DiscardKafkaDeadLetterRecordResult result =
            service.discard(COMMAND);

        assertThat(result)
            .isSameAs(portResult);

        verify(discardPort)
            .discard(RECORD_ID);
    }
}
