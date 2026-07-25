package com.nursena.payflow.eventprocessing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditFilter;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditPage;
import com.nursena.payflow.eventprocessing.application.port.in
    .ListKafkaDeadLetterCommandAuditsQuery;
import com.nursena.payflow.eventprocessing.application.port.out
    .KafkaDeadLetterCommandAuditQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListKafkaDeadLetterCommandAuditsServiceTest {

    @Mock
    private KafkaDeadLetterCommandAuditQueryPort
        queryPort;

    private ListKafkaDeadLetterCommandAuditsService
        service;

    @BeforeEach
    void setUp() {
        service =
            new ListKafkaDeadLetterCommandAuditsService(
                queryPort
            );
    }

    @Test
    void shouldDelegatePageQueryToPort() {
        KafkaDeadLetterCommandAuditFilter filter =
            KafkaDeadLetterCommandAuditFilter
                .unfiltered();
        ListKafkaDeadLetterCommandAuditsQuery query =
            new ListKafkaDeadLetterCommandAuditsQuery(
                2,
                20,
                filter
            );
        KafkaDeadLetterCommandAuditPage expectedPage =
            new KafkaDeadLetterCommandAuditPage(
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

        KafkaDeadLetterCommandAuditPage result =
            service.listKafkaDeadLetterCommandAudits(
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
                service.listKafkaDeadLetterCommandAudits(
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

    @Test
    void shouldRejectNullQueryPort() {
        assertThatThrownBy(
            () ->
                new ListKafkaDeadLetterCommandAuditsService(
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
}
