package com.nursena.payflow.eventprocessing.adapter.in.web;

import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationConfiguration;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet
    .request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet
    .request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet
    .result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet
    .result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.configuration
    .SecurityConfiguration;
import com.nursena.payflow.configuration.security
    .OperationsAuthorities;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterRecordDetails;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterRecordFilter;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterRecordPage;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterRecordSummary;
import com.nursena.payflow.eventprocessing.application.port.in
    .GetKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.in
    .ListKafkaDeadLetterRecordsQuery;
import com.nursena.payflow.eventprocessing.application.port.in
    .ListKafkaDeadLetterRecordsUseCase;
import com.nursena.payflow.eventprocessing.domain.exception
    .KafkaDeadLetterRecordNotFoundException;
import com.nursena.payflow.eventprocessing.domain.model
    .KafkaDeadLetterRecordStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
    .WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority
    .SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito
    .MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KafkaDeadLetterQueryController.class)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    KafkaDeadLetterQueryExceptionHandler.class
})
class KafkaDeadLetterQueryControllerTest {

    private static final String COLLECTION_PATH =
        "/api/v1/operations/kafka/dead-letters";

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001203"
        );

    private static final UUID REPLAY_ORIGIN_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001200"
        );

    private static final Instant RECEIVED_AT =
        Instant.parse(
            "2026-07-22T12:02:00Z"
        );

    private static final Instant LAST_REPLAYED_AT =
        Instant.parse(
            "2026-07-22T12:03:00Z"
        );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListKafkaDeadLetterRecordsUseCase
        listUseCase;

    @MockitoBean
    private GetKafkaDeadLetterRecordUseCase
        getUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnAuthorizedDeadLetterPage()
        throws Exception {

        ListKafkaDeadLetterRecordsQuery expectedQuery =
            new ListKafkaDeadLetterRecordsQuery(
                0,
                20,
                KafkaDeadLetterRecordFilter
                    .unfiltered()
            );

        when(
            listUseCase.listKafkaDeadLetterRecords(
                expectedQuery
            )
        ).thenReturn(
            new KafkaDeadLetterRecordPage(
                List.of(summary()),
                0,
                20,
                1,
                1
            )
        );

        mockMvc.perform(
                get(COLLECTION_PATH)
                    .with(operatorJwt())
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.items.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.items[0].id")
                    .value(RECORD_ID.toString())
            )
            .andExpect(
                jsonPath("$.items[0].status")
                    .value("REPLAY_FAILED")
            )
            .andExpect(
                jsonPath(
                    "$.items[0].deadLetterTopic"
                )
                    .value(
                        "wallet.transfer.completed.dlt"
                    )
            )
            .andExpect(
                jsonPath(
                    "$.items[0].deadLetterPartition"
                )
                    .value(0)
            )
            .andExpect(
                jsonPath(
                    "$.items[0].deadLetterOffset"
                )
                    .value(203)
            )
            .andExpect(
                jsonPath(
                    "$.items[0].originalTopic"
                )
                    .value(
                        "wallet.transfer.completed"
                    )
            )
            .andExpect(
                jsonPath(
                    "$.items[0].originalPartition"
                )
                    .value(0)
            )
            .andExpect(
                jsonPath(
                    "$.items[0].originalOffset"
                )
                    .value(103)
            )
            .andExpect(
                jsonPath(
                    "$.items[0].originalConsumerGroup"
                )
                    .value(
                        "payflow-transfer-"
                            + "completed-audit-v1"
                    )
            )
            .andExpect(
                jsonPath(
                    "$.items[0].exceptionType"
                )
                    .value(
                        "java.lang."
                            + "IllegalStateException"
                    )
            )
            .andExpect(
                jsonPath(
                    "$.items[0].replayCount"
                )
                    .value(1)
            )
            .andExpect(
                jsonPath(
                    "$.items[0].replayAttemptBase"
                )
                    .value(2)
            )
            .andExpect(
                jsonPath(
                    "$.items[0].totalReplayAttempts"
                )
                    .value(3)
            )
            .andExpect(
                jsonPath(
                    "$.items[0].receivedAt"
                )
                    .value(RECEIVED_AT.toString())
            )
            .andExpect(
                jsonPath(
                    "$.items[0].lastReplayedAt"
                )
                    .value(
                        LAST_REPLAYED_AT.toString()
                    )
            )
            .andExpect(
                jsonPath(
                    "$.items[0].replayOriginId"
                )
                    .value(
                        REPLAY_ORIGIN_ID.toString()
                    )
            )
            .andExpect(
                jsonPath(
                    "$.items[0].payloadAvailable"
                )
                    .value(true)
            )
            .andExpect(
                jsonPath("$.items[0].payload")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.items[0].recordKey")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath(
                    "$.items[0].replayLeaseOwner"
                )
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.page")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.size")
                    .value(20)
            )
            .andExpect(
                jsonPath("$.totalElements")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.totalPages")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.first")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.last")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.hasNext")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.hasPrevious")
                    .value(false)
            );

        verify(listUseCase)
            .listKafkaDeadLetterRecords(
                expectedQuery
            );

        verifyNoInteractions(getUseCase);
    }

    @Test
    void shouldApplyPaginationAndStatusFilter()
        throws Exception {

        KafkaDeadLetterRecordFilter filter =
            new KafkaDeadLetterRecordFilter(
                KafkaDeadLetterRecordStatus
                    .REPLAY_FAILED
            );

        ListKafkaDeadLetterRecordsQuery expectedQuery =
            new ListKafkaDeadLetterRecordsQuery(
                2,
                10,
                filter
            );

        when(
            listUseCase.listKafkaDeadLetterRecords(
                expectedQuery
            )
        ).thenReturn(
            new KafkaDeadLetterRecordPage(
                List.of(),
                2,
                10,
                21,
                3
            )
        );

        mockMvc.perform(
                get(COLLECTION_PATH)
                    .param("page", "2")
                    .param("size", "10")
                    .param(
                        "status",
                        "REPLAY_FAILED"
                    )
                    .with(operatorJwt())
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.items.length()")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.page")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.size")
                    .value(10)
            )
            .andExpect(
                jsonPath("$.totalElements")
                    .value(21)
            )
            .andExpect(
                jsonPath("$.totalPages")
                    .value(3)
            )
            .andExpect(
                jsonPath("$.first")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.last")
                    .value(true)
            );

        verify(listUseCase)
            .listKafkaDeadLetterRecords(
                expectedQuery
            );
    }

    @Test
    void shouldReturnAuthorizedRecordDetails()
        throws Exception {

        when(
            getUseCase.getKafkaDeadLetterRecord(
                RECORD_ID
            )
        ).thenReturn(details());

        mockMvc.perform(
                get(
                    COLLECTION_PATH + "/{recordId}",
                    RECORD_ID
                )
                    .with(operatorJwt())
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.id")
                    .value(RECORD_ID.toString())
            )
            .andExpect(
                jsonPath("$.status")
                    .value("REPLAY_FAILED")
            )
            .andExpect(
                jsonPath("$.totalReplayAttempts")
                    .value(3)
            )
            .andExpect(
                jsonPath("$.payloadAvailable")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.exceptionMessage")
                    .value(
                        "Transfer processing failed."
                    )
            )
            .andExpect(
                jsonPath("$.lastReplayError")
                    .value(
                        "Replay publication failed."
                    )
            )
            .andExpect(
                jsonPath("$.replayLeaseUntil")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.summary")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.payload")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.recordKey")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.replayLeaseOwner")
                    .doesNotExist()
            );

        verify(getUseCase)
            .getKafkaDeadLetterRecord(
                RECORD_ID
            );

        verifyNoInteractions(listUseCase);
    }

    @Test
    void shouldRejectAnonymousCollectionRequest()
        throws Exception {

        mockMvc.perform(
                get(COLLECTION_PATH)
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(
            listUseCase,
            getUseCase
        );
    }

    @Test
    void shouldRejectAuthenticatedNonOperator()
        throws Exception {

        mockMvc.perform(
                get(COLLECTION_PATH)
                    .with(nonOperatorJwt())
            )
            .andExpect(status().isForbidden());

        verifyNoInteractions(
            listUseCase,
            getUseCase
        );
    }

    @Test
    void shouldRejectAnonymousDetailRequest()
        throws Exception {

        mockMvc.perform(
                get(
                    COLLECTION_PATH + "/{recordId}",
                    RECORD_ID
                )
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(
            listUseCase,
            getUseCase
        );
    }

    @Test
    void shouldRejectNonOperatorDetailRequest()
        throws Exception {

        mockMvc.perform(
                get(
                    COLLECTION_PATH + "/{recordId}",
                    RECORD_ID
                )
                    .with(nonOperatorJwt())
            )
            .andExpect(status().isForbidden());

        verifyNoInteractions(
            listUseCase,
            getUseCase
        );
    }

    @Test
    void shouldRejectNegativePage()
        throws Exception {

        mockMvc.perform(
                get(COLLECTION_PATH)
                    .param("page", "-1")
                    .with(operatorJwt())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Request validation failed."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(COLLECTION_PATH)
            );

        verifyNoInteractions(
            listUseCase,
            getUseCase
        );
    }

    @Test
    void shouldRejectPageSizeAboveMaximum()
        throws Exception {

        mockMvc.perform(
                get(COLLECTION_PATH)
                    .param("size", "101")
                    .with(operatorJwt())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            );

        verifyNoInteractions(
            listUseCase,
            getUseCase
        );
    }

    @Test
    void shouldRejectUnknownStatus()
        throws Exception {

        mockMvc.perform(
                get(COLLECTION_PATH)
                    .param("status", "UNKNOWN")
                    .with(operatorJwt())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            )
            .andExpect(
                jsonPath("$.path")
                    .value(COLLECTION_PATH)
            );

        verifyNoInteractions(
            listUseCase,
            getUseCase
        );
    }

    @Test
    void shouldRejectMalformedRecordIdentifier()
        throws Exception {

        mockMvc.perform(
                get(
                    COLLECTION_PATH
                        + "/not-a-uuid"
                )
                    .with(operatorJwt())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        COLLECTION_PATH
                            + "/not-a-uuid"
                    )
            );

        verifyNoInteractions(
            listUseCase,
            getUseCase
        );
    }

    @Test
    void shouldReturnNotFoundForUnknownRecord()
        throws Exception {

        when(
            getUseCase.getKafkaDeadLetterRecord(
                RECORD_ID
            )
        ).thenThrow(
            new KafkaDeadLetterRecordNotFoundException(
                RECORD_ID
            )
        );

        mockMvc.perform(
                get(
                    COLLECTION_PATH + "/{recordId}",
                    RECORD_ID
                )
                    .with(operatorJwt())
            )
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "KAFKA_DEAD_LETTER_"
                            + "RECORD_NOT_FOUND"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Kafka dead-letter record "
                            + "was not found."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        COLLECTION_PATH
                            + "/"
                            + RECORD_ID
                    )
            );

        verify(getUseCase)
            .getKafkaDeadLetterRecord(
                RECORD_ID
            );

        verifyNoInteractions(listUseCase);
    }

    private static KafkaDeadLetterRecordSummary
    summary() {
        return new KafkaDeadLetterRecordSummary(
            RECORD_ID,
            KafkaDeadLetterRecordStatus
                .REPLAY_FAILED,
            "wallet.transfer.completed.dlt",
            0,
            203L,
            "wallet.transfer.completed",
            0,
            103L,
            "payflow-transfer-completed-audit-v1",
            "java.lang.IllegalStateException",
            1,
            2,
            RECEIVED_AT,
            LAST_REPLAYED_AT,
            REPLAY_ORIGIN_ID,
            true
        );
    }

    private static KafkaDeadLetterRecordDetails
    details() {
        return new KafkaDeadLetterRecordDetails(
            summary(),
            "Transfer processing failed.",
            "Replay publication failed.",
            null
        );
    }

    private static org.springframework.test.web.servlet
        .request.RequestPostProcessor
    operatorJwt() {
        return jwt()
            .authorities(
                new SimpleGrantedAuthority(
                    OperationsAuthorities.OPERATIONS
                )
            );
    }

    private static org.springframework.test.web.servlet
        .request.RequestPostProcessor
    nonOperatorJwt() {
        return jwt()
            .authorities(
                new SimpleGrantedAuthority(
                    "PAYFLOW_CUSTOMER"
                )
            );
    }
}
