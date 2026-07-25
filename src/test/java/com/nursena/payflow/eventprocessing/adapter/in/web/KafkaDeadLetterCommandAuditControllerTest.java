package com.nursena.payflow.eventprocessing.adapter.in.web;

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

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.configuration.security
    .OperationsAuthorities;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAudit;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditFilter;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditOutcome;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditPage;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditStage;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditTimeline;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandType;
import com.nursena.payflow.eventprocessing.application.port.in
    .GetKafkaDeadLetterCommandAuditTimelineUseCase;
import com.nursena.payflow.eventprocessing.application.port.in
    .ListKafkaDeadLetterCommandAuditsQuery;
import com.nursena.payflow.eventprocessing.application.port.in
    .ListKafkaDeadLetterCommandAuditsUseCase;
import com.nursena.payflow.eventprocessing.domain.exception
    .KafkaDeadLetterCommandAuditTimelineNotFoundException;
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

@WebMvcTest(KafkaDeadLetterCommandAuditController.class)
@Import({
    SecurityConfiguration.class,
    KafkaDeadLetterCommandAuditExceptionHandler.class
})
class KafkaDeadLetterCommandAuditControllerTest {

    private static final String COLLECTION_PATH =
        "/api/v1/operations/kafka/"
            + "dead-letter-command-audits";

    private static final UUID AUDIT_ID =
        UUID.fromString(
            "90000000-0000-0000-0000-000000000001"
        );

    private static final UUID COMPLETED_AUDIT_ID =
        UUID.fromString(
            "90000000-0000-0000-0000-000000000002"
        );

    private static final UUID COMMAND_ID =
        UUID.fromString(
            "90000000-0000-0000-0000-000000000003"
        );

    private static final UUID OPERATOR_ID =
        UUID.fromString(
            "90000000-0000-0000-0000-000000000004"
        );

    private static final UUID RECORD_ID =
        UUID.fromString(
            "90000000-0000-0000-0000-000000000005"
        );

    private static final Instant ATTEMPTED_AT =
        Instant.parse("2026-07-25T12:00:00Z");

    private static final Instant COMPLETED_AT =
        Instant.parse("2026-07-25T12:00:01Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListKafkaDeadLetterCommandAuditsUseCase
        listUseCase;

    @MockitoBean
    private GetKafkaDeadLetterCommandAuditTimelineUseCase
        getTimelineUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnAuthorizedAuditPage()
        throws Exception {

        ListKafkaDeadLetterCommandAuditsQuery expectedQuery =
            new ListKafkaDeadLetterCommandAuditsQuery(
                0,
                20,
                KafkaDeadLetterCommandAuditFilter
                    .unfiltered()
            );

        when(
            listUseCase.listKafkaDeadLetterCommandAudits(
                expectedQuery
            )
        ).thenReturn(
            new KafkaDeadLetterCommandAuditPage(
                List.of(attempted()),
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
                    .value(AUDIT_ID.toString())
            )
            .andExpect(
                jsonPath("$.items[0].commandId")
                    .value(COMMAND_ID.toString())
            )
            .andExpect(
                jsonPath("$.items[0].stage")
                    .value("ATTEMPTED")
            )
            .andExpect(
                jsonPath("$.items[0].operatorId")
                    .value(OPERATOR_ID.toString())
            )
            .andExpect(
                jsonPath("$.items[0].deadLetterRecordId")
                    .value(RECORD_ID.toString())
            )
            .andExpect(
                jsonPath("$.items[0].commandType")
                    .value("REPLAY")
            )
            .andExpect(
                jsonPath("$.items[0].outcome")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.items[0].errorCode")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.items[0].occurredAt")
                    .value(ATTEMPTED_AT.toString())
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
                jsonPath("$.items[0].operatorEmail")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.items[0].exceptionMessage")
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
            );

        verify(listUseCase)
            .listKafkaDeadLetterCommandAudits(
                expectedQuery
            );
        verifyNoInteractions(getTimelineUseCase);
    }

    @Test
    void shouldApplyEverySupportedFilter()
        throws Exception {

        KafkaDeadLetterCommandAuditFilter filter =
            new KafkaDeadLetterCommandAuditFilter(
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.REPLAY,
                KafkaDeadLetterCommandAuditStage.COMPLETED,
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_FAILED
            );

        ListKafkaDeadLetterCommandAuditsQuery expectedQuery =
            new ListKafkaDeadLetterCommandAuditsQuery(
                2,
                10,
                filter
            );

        when(
            listUseCase.listKafkaDeadLetterCommandAudits(
                expectedQuery
            )
        ).thenReturn(
            new KafkaDeadLetterCommandAuditPage(
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
                        "commandId",
                        COMMAND_ID.toString()
                    )
                    .param(
                        "operatorId",
                        OPERATOR_ID.toString()
                    )
                    .param(
                        "deadLetterRecordId",
                        RECORD_ID.toString()
                    )
                    .param("commandType", "REPLAY")
                    .param("stage", "COMPLETED")
                    .param("outcome", "REPLAY_FAILED")
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
            );

        verify(listUseCase)
            .listKafkaDeadLetterCommandAudits(
                expectedQuery
            );
    }

    @Test
    void shouldReturnCompleteTimeline()
        throws Exception {

        KafkaDeadLetterCommandAuditTimeline timeline =
            new KafkaDeadLetterCommandAuditTimeline(
                COMMAND_ID,
                List.of(
                    attempted(),
                    completed()
                )
            );

        when(
            getTimelineUseCase
                .getKafkaDeadLetterCommandAuditTimeline(
                    COMMAND_ID
                )
        ).thenReturn(timeline);

        mockMvc.perform(
                get(
                    COLLECTION_PATH + "/{commandId}",
                    COMMAND_ID
                )
                    .with(operatorJwt())
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.commandId")
                    .value(COMMAND_ID.toString())
            )
            .andExpect(
                jsonPath("$.complete")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.entries.length()")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.entries[0].stage")
                    .value("ATTEMPTED")
            )
            .andExpect(
                jsonPath("$.entries[1].stage")
                    .value("COMPLETED")
            )
            .andExpect(
                jsonPath("$.entries[1].outcome")
                    .value("REPLAY_FAILED")
            )
            .andExpect(
                jsonPath("$.entries[1].errorCode")
                    .value(
                        "KAFKA_DEAD_LETTER_REPLAY_FAILED"
                    )
            );

        verify(getTimelineUseCase)
            .getKafkaDeadLetterCommandAuditTimeline(
                COMMAND_ID
            );
        verifyNoInteractions(listUseCase);
    }

    @Test
    void shouldReturnIncompleteTimeline()
        throws Exception {

        when(
            getTimelineUseCase
                .getKafkaDeadLetterCommandAuditTimeline(
                    COMMAND_ID
                )
        ).thenReturn(
            new KafkaDeadLetterCommandAuditTimeline(
                COMMAND_ID,
                List.of(attempted())
            )
        );

        mockMvc.perform(
                get(
                    COLLECTION_PATH + "/{commandId}",
                    COMMAND_ID
                )
                    .with(operatorJwt())
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.complete")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.entries.length()")
                    .value(1)
            );
    }

    @Test
    void shouldRejectAnonymousRequest()
        throws Exception {

        mockMvc.perform(get(COLLECTION_PATH))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(
            listUseCase,
            getTimelineUseCase
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
            getTimelineUseCase
        );
    }

    @Test
    void shouldRejectInvalidPageSize()
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
            getTimelineUseCase
        );
    }

    @Test
    void shouldRejectMalformedFilterIdentifier()
        throws Exception {

        mockMvc.perform(
                get(COLLECTION_PATH)
                    .param("commandId", "not-a-uuid")
                    .with(operatorJwt())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            );

        verifyNoInteractions(
            listUseCase,
            getTimelineUseCase
        );
    }

    @Test
    void shouldRejectUnknownOutcome()
        throws Exception {

        mockMvc.perform(
                get(COLLECTION_PATH)
                    .param("outcome", "UNKNOWN")
                    .with(operatorJwt())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            );

        verifyNoInteractions(
            listUseCase,
            getTimelineUseCase
        );
    }

    @Test
    void shouldReturnNotFoundForUnknownTimeline()
        throws Exception {

        when(
            getTimelineUseCase
                .getKafkaDeadLetterCommandAuditTimeline(
                    COMMAND_ID
                )
        ).thenThrow(
            new KafkaDeadLetterCommandAuditTimelineNotFoundException(
                COMMAND_ID
            )
        );

        mockMvc.perform(
                get(
                    COLLECTION_PATH + "/{commandId}",
                    COMMAND_ID
                )
                    .with(operatorJwt())
            )
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "KAFKA_DEAD_LETTER_COMMAND_"
                            + "AUDIT_TIMELINE_NOT_FOUND"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Kafka dead-letter command audit "
                            + "timeline was not found."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        COLLECTION_PATH
                            + "/"
                            + COMMAND_ID
                    )
            );
    }

    @Test
    void shouldRejectMalformedTimelineIdentifier()
        throws Exception {

        mockMvc.perform(
                get(COLLECTION_PATH + "/not-a-uuid")
                    .with(operatorJwt())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            );

        verifyNoInteractions(
            listUseCase,
            getTimelineUseCase
        );
    }

    private static KafkaDeadLetterCommandAudit attempted() {
        return KafkaDeadLetterCommandAudit.attempted(
            AUDIT_ID,
            COMMAND_ID,
            OPERATOR_ID,
            RECORD_ID,
            KafkaDeadLetterCommandType.REPLAY,
            ATTEMPTED_AT
        );
    }

    private static KafkaDeadLetterCommandAudit completed() {
        return KafkaDeadLetterCommandAudit.completed(
            COMPLETED_AUDIT_ID,
            COMMAND_ID,
            OPERATOR_ID,
            RECORD_ID,
            KafkaDeadLetterCommandType.REPLAY,
            KafkaDeadLetterCommandAuditOutcome
                .REPLAY_FAILED,
            COMPLETED_AT
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
