package com.nursena.payflow.eventprocessing.adapter.in.web;

import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationConfiguration;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet
    .request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet
    .request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet
    .result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet
    .result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet
    .result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.nursena.payflow.configuration
    .SecurityConfiguration;
import com.nursena.payflow.configuration.security
    .OperationsAuthorities;
import com.nursena.payflow.eventprocessing.application.model
    .OperatorDiscardKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model
    .DiscardKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.model
    .OperatorReplayKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model
    .ReplayKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.in
    .OperatorDiscardKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.in
    .OperatorReplayKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.domain.exception
    .KafkaDeadLetterCommandAuditException;
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
import org.springframework.test.web.servlet.request
    .RequestPostProcessor;

@WebMvcTest(
    KafkaDeadLetterCommandController.class
)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    KafkaDeadLetterCommandExceptionHandler.class
})
class KafkaDeadLetterCommandControllerTest {

    private static final UUID OPERATOR_ID =
        UUID.fromString(
            "70000000-0000-0000-0000-000000002401"
        );

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002401"
        );

    private static final String BASE_PATH =
        "/api/v1/operations/kafka/dead-letters";

    private static final String REPLAY_PATH =
        BASE_PATH + "/" + RECORD_ID + "/replay";

    private static final String DISCARD_PATH =
        BASE_PATH + "/" + RECORD_ID + "/discard";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperatorReplayKafkaDeadLetterRecordUseCase
        replayUseCase;

    @MockitoBean
    private OperatorDiscardKafkaDeadLetterRecordUseCase
        discardUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReplayRecordForAuthorizedOperator()
        throws Exception {

        OperatorReplayKafkaDeadLetterRecordCommand command =
            new OperatorReplayKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        when(
            replayUseCase.replay(command)
        ).thenReturn(
            ReplayKafkaDeadLetterRecordResult
                .replayed()
        );

        mockMvc.perform(
                post(REPLAY_PATH)
                    .with(operatorJwt())
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.recordId")
                    .value(RECORD_ID.toString())
            )
            .andExpect(
                jsonPath("$.status")
                    .value("REPLAYED")
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

        verify(replayUseCase)
            .replay(command);

        verifyNoInteractions(discardUseCase);
    }

    @Test
    void shouldReturnNotFoundWhenReplayRecordIsMissing()
        throws Exception {

        OperatorReplayKafkaDeadLetterRecordCommand command =
            new OperatorReplayKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        when(
            replayUseCase.replay(command)
        ).thenReturn(
            ReplayKafkaDeadLetterRecordResult
                .notFound()
        );

        mockMvc.perform(
                post(REPLAY_PATH)
                    .with(operatorJwt())
            )
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.status")
                    .value(404)
            )
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
                    .value(REPLAY_PATH)
            );

        verify(replayUseCase)
            .replay(command);

        verifyNoInteractions(discardUseCase);
    }

    @Test
    void shouldReturnConflictWhenRecordCannotBeReplayed()
        throws Exception {

        OperatorReplayKafkaDeadLetterRecordCommand command =
            new OperatorReplayKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        when(
            replayUseCase.replay(command)
        ).thenReturn(
            ReplayKafkaDeadLetterRecordResult
                .notClaimable()
        );

        mockMvc.perform(
                post(REPLAY_PATH)
                    .with(operatorJwt())
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.status")
                    .value(409)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "KAFKA_DEAD_LETTER_"
                            + "RECORD_NOT_CLAIMABLE"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Kafka dead-letter record "
                            + "cannot be replayed in "
                            + "its current state."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(REPLAY_PATH)
            );

        verify(replayUseCase)
            .replay(command);

        verifyNoInteractions(discardUseCase);
    }

    @Test
    void shouldReturnBadGatewayWhenReplayPublicationFails()
        throws Exception {

        OperatorReplayKafkaDeadLetterRecordCommand command =
            new OperatorReplayKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        when(
            replayUseCase.replay(command)
        ).thenReturn(
            ReplayKafkaDeadLetterRecordResult
                .replayFailed()
        );

        mockMvc.perform(
                post(REPLAY_PATH)
                    .with(operatorJwt())
            )
            .andExpect(status().isBadGateway())
            .andExpect(
                jsonPath("$.status")
                    .value(502)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "KAFKA_DEAD_LETTER_"
                            + "REPLAY_FAILED"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Kafka dead-letter replay "
                            + "publication failed."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(REPLAY_PATH)
            );

        verify(replayUseCase)
            .replay(command);

        verifyNoInteractions(discardUseCase);
    }

    @Test
    void shouldReturnServiceUnavailableForUnresolvedReplay()
        throws Exception {

        OperatorReplayKafkaDeadLetterRecordCommand command =
            new OperatorReplayKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        when(
            replayUseCase.replay(command)
        ).thenReturn(
            ReplayKafkaDeadLetterRecordResult
                .unresolved()
        );

        mockMvc.perform(
                post(REPLAY_PATH)
                    .with(operatorJwt())
            )
            .andExpect(
                status().isServiceUnavailable()
            )
            .andExpect(
                jsonPath("$.status")
                    .value(503)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "KAFKA_DEAD_LETTER_"
                            + "REPLAY_UNRESOLVED"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Kafka dead-letter replay "
                            + "outcome could not be "
                            + "resolved."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(REPLAY_PATH)
            );

        verify(replayUseCase)
            .replay(command);

        verifyNoInteractions(discardUseCase);
    }

    @Test
    void shouldDiscardRecordForAuthorizedOperator()
        throws Exception {

        OperatorDiscardKafkaDeadLetterRecordCommand command =
            new OperatorDiscardKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        when(
            discardUseCase.discard(command)
        ).thenReturn(
            DiscardKafkaDeadLetterRecordResult
                .discarded()
        );

        mockMvc.perform(
                post(DISCARD_PATH)
                    .with(operatorJwt())
            )
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(discardUseCase)
            .discard(command);

        verifyNoInteractions(replayUseCase);
    }

    @Test
    void shouldTreatRepeatedDiscardAsSuccessful()
        throws Exception {

        OperatorDiscardKafkaDeadLetterRecordCommand command =
            new OperatorDiscardKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        when(
            discardUseCase.discard(command)
        ).thenReturn(
            DiscardKafkaDeadLetterRecordResult
                .alreadyDiscarded()
        );

        mockMvc.perform(
                post(DISCARD_PATH)
                    .with(operatorJwt())
            )
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(discardUseCase)
            .discard(command);

        verifyNoInteractions(replayUseCase);
    }

    @Test
    void shouldReturnNotFoundWhenDiscardRecordIsMissing()
        throws Exception {

        OperatorDiscardKafkaDeadLetterRecordCommand command =
            new OperatorDiscardKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        when(
            discardUseCase.discard(command)
        ).thenReturn(
            DiscardKafkaDeadLetterRecordResult
                .notFound()
        );

        mockMvc.perform(
                post(DISCARD_PATH)
                    .with(operatorJwt())
            )
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.status")
                    .value(404)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "KAFKA_DEAD_LETTER_"
                            + "RECORD_NOT_FOUND"
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(DISCARD_PATH)
            );

        verify(discardUseCase)
            .discard(command);

        verifyNoInteractions(replayUseCase);
    }

    @Test
    void shouldReturnConflictWhenRecordCannotBeDiscarded()
        throws Exception {

        OperatorDiscardKafkaDeadLetterRecordCommand command =
            new OperatorDiscardKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        when(
            discardUseCase.discard(command)
        ).thenReturn(
            DiscardKafkaDeadLetterRecordResult
                .notDiscardable()
        );

        mockMvc.perform(
                post(DISCARD_PATH)
                    .with(operatorJwt())
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.status")
                    .value(409)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "KAFKA_DEAD_LETTER_"
                            + "RECORD_NOT_DISCARDABLE"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Kafka dead-letter record "
                            + "cannot be discarded in "
                            + "its current state."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(DISCARD_PATH)
            );

        verify(discardUseCase)
            .discard(command);

        verifyNoInteractions(replayUseCase);
    }

    @Test
    void shouldRejectAnonymousReplayRequest()
        throws Exception {

        mockMvc.perform(
                post(REPLAY_PATH)
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(
            replayUseCase,
            discardUseCase
        );
    }

    @Test
    void shouldRejectNonOperatorReplayRequest()
        throws Exception {

        mockMvc.perform(
                post(REPLAY_PATH)
                    .with(nonOperatorJwt())
            )
            .andExpect(status().isForbidden());

        verifyNoInteractions(
            replayUseCase,
            discardUseCase
        );
    }

    @Test
    void shouldRejectAnonymousDiscardRequest()
        throws Exception {

        mockMvc.perform(
                post(DISCARD_PATH)
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(
            replayUseCase,
            discardUseCase
        );
    }

    @Test
    void shouldRejectNonOperatorDiscardRequest()
        throws Exception {

        mockMvc.perform(
                post(DISCARD_PATH)
                    .with(nonOperatorJwt())
            )
            .andExpect(status().isForbidden());

        verifyNoInteractions(
            replayUseCase,
            discardUseCase
        );
    }

    @Test
    void shouldRejectMalformedOperatorIdentity()
        throws Exception {

        mockMvc.perform(
                post(REPLAY_PATH)
                    .with(malformedOperatorJwt())
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.status")
                    .value(401)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "KAFKA_DEAD_LETTER_OPERATOR_"
                            + "IDENTITY_INVALID"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Authenticated operator "
                            + "identity is invalid."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(REPLAY_PATH)
            );

        verifyNoInteractions(
            replayUseCase,
            discardUseCase
        );
    }

    @Test
    void shouldReturnServiceUnavailableWhenAuditAttemptFails()
        throws Exception {

        OperatorReplayKafkaDeadLetterRecordCommand command =
            new OperatorReplayKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        when(
            replayUseCase.replay(command)
        ).thenThrow(
            KafkaDeadLetterCommandAuditException
                .attemptPersistenceFailed(
                    new IllegalStateException(
                        "sensitive database detail"
                    )
                )
        );

        mockMvc.perform(
                post(REPLAY_PATH)
                    .with(operatorJwt())
            )
            .andExpect(
                status().isServiceUnavailable()
            )
            .andExpect(
                jsonPath("$.status")
                    .value(503)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "KAFKA_DEAD_LETTER_COMMAND_"
                            + "AUDIT_UNAVAILABLE"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Kafka dead-letter command "
                            + "audit attempt could not "
                            + "be persisted."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(REPLAY_PATH)
            );

        verify(replayUseCase)
            .replay(command);

        verifyNoInteractions(discardUseCase);
    }

    @Test
    void shouldReturnServiceUnavailableWhenCompletionAuditFails()
        throws Exception {

        OperatorDiscardKafkaDeadLetterRecordCommand command =
            new OperatorDiscardKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        when(
            discardUseCase.discard(command)
        ).thenThrow(
            KafkaDeadLetterCommandAuditException
                .completionPersistenceFailed(
                    new IllegalStateException(
                        "sensitive database detail"
                    )
                )
        );

        mockMvc.perform(
                post(DISCARD_PATH)
                    .with(operatorJwt())
            )
            .andExpect(
                status().isServiceUnavailable()
            )
            .andExpect(
                jsonPath("$.status")
                    .value(503)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "KAFKA_DEAD_LETTER_COMMAND_"
                            + "AUDIT_COMPLETION_UNAVAILABLE"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Kafka dead-letter command "
                            + "completion could not be "
                            + "audited safely."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(DISCARD_PATH)
            );

        verify(discardUseCase)
            .discard(command);

        verifyNoInteractions(replayUseCase);
    }

    @Test
    void shouldReturnInternalServerErrorForUnexpectedCommandFailure()
        throws Exception {

        OperatorReplayKafkaDeadLetterRecordCommand command =
            new OperatorReplayKafkaDeadLetterRecordCommand(
                OPERATOR_ID,
                RECORD_ID
            );

        when(
            replayUseCase.replay(command)
        ).thenThrow(
            KafkaDeadLetterCommandAuditException
                .commandInternalFailure(
                    new IllegalStateException(
                        "sensitive command detail"
                    )
                )
        );

        mockMvc.perform(
                post(REPLAY_PATH)
                    .with(operatorJwt())
            )
            .andExpect(
                status().isInternalServerError()
            )
            .andExpect(
                jsonPath("$.status")
                    .value(500)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "KAFKA_DEAD_LETTER_COMMAND_"
                            + "INTERNAL_FAILURE"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Kafka dead-letter command "
                            + "failed unexpectedly."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(REPLAY_PATH)
            );

        verify(replayUseCase)
            .replay(command);

        verifyNoInteractions(discardUseCase);
    }

    @Test
    void shouldRejectMalformedReplayRecordIdentifier()
        throws Exception {

        String path =
            BASE_PATH + "/not-a-uuid/replay";

        mockMvc.perform(
                post(path)
                    .with(operatorJwt())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.status")
                    .value(400)
            )
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
                    .value(path)
            );

        verifyNoInteractions(
            replayUseCase,
            discardUseCase
        );
    }

    @Test
    void shouldRejectMalformedDiscardRecordIdentifier()
        throws Exception {

        String path =
            BASE_PATH + "/not-a-uuid/discard";

        mockMvc.perform(
                post(path)
                    .with(operatorJwt())
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.status")
                    .value(400)
            )
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            )
            .andExpect(
                jsonPath("$.path")
                    .value(path)
            );

        verifyNoInteractions(
            replayUseCase,
            discardUseCase
        );
    }

    private static RequestPostProcessor
    operatorJwt() {
        return jwt()
            .jwt(
                builder -> builder.subject(
                    OPERATOR_ID.toString()
                )
            )
            .authorities(
                new SimpleGrantedAuthority(
                    OperationsAuthorities.OPERATIONS
                )
            );
    }

    private static RequestPostProcessor
    nonOperatorJwt() {
        return jwt()
            .jwt(
                builder -> builder.subject(
                    OPERATOR_ID.toString()
                )
            )
            .authorities(
                new SimpleGrantedAuthority(
                    "PAYFLOW_CUSTOMER"
                )
            );
    }

    private static RequestPostProcessor
    malformedOperatorJwt() {
        return jwt()
            .jwt(
                builder -> builder.subject(
                    "not-a-uuid"
                )
            )
            .authorities(
                new SimpleGrantedAuthority(
                    OperationsAuthorities.OPERATIONS
                )
            );
    }
}
