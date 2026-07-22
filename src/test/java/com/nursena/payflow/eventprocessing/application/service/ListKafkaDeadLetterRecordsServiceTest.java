package com.nursena.payflow.eventprocessing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordFilter;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordPage;
import com.nursena.payflow.eventprocessing.application.port.in.ListKafkaDeadLetterRecordsQuery;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterQueryPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListKafkaDeadLetterRecordsServiceTest {

    @Mock
    private KafkaDeadLetterQueryPort queryPort;

    private ListKafkaDeadLetterRecordsService service;

    @BeforeEach
    void setUp() {
        service =
            new ListKafkaDeadLetterRecordsService(
                queryPort
            );
    }

    @Test
    void shouldDelegatePageQueryToPort() {
        KafkaDeadLetterRecordFilter filter =
            new KafkaDeadLetterRecordFilter(
                KafkaDeadLetterRecordStatus
                    .REPLAY_FAILED
            );

        ListKafkaDeadLetterRecordsQuery query =
            new ListKafkaDeadLetterRecordsQuery(
                2,
                20,
                filter
            );

        KafkaDeadLetterRecordPage expectedPage =
            new KafkaDeadLetterRecordPage(
                List.of(),
                2,
                20,
                0,
                0
            );

        when(
            queryPort.findPage(
                2,
                20,
                filter
            )
        ).thenReturn(expectedPage);

        KafkaDeadLetterRecordPage result =
            service.listKafkaDeadLetterRecords(
                query
            );

        assertThat(result).isSameAs(expectedPage);

        verify(queryPort).findPage(
            2,
            20,
            filter
        );
    }

    @Test
    void shouldRejectNullQuery() {
        assertThatThrownBy(
            () ->
                service.listKafkaDeadLetterRecords(
                    null
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "query must not be null"
            );

        verifyNoInteractions(queryPort);
    }
}
