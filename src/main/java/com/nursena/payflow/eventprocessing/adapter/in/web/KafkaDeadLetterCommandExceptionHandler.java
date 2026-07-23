package com.nursena.payflow.eventprocessing.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.eventprocessing.domain.exception
    .KafkaDeadLetterCommandAuditException;
import com.nursena.payflow.eventprocessing.domain.exception
    .KafkaDeadLetterCommandException;
import com.nursena.payflow.eventprocessing.domain.exception
    .KafkaDeadLetterRecordNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation
    .ExceptionHandler;
import org.springframework.web.bind.annotation
    .RestControllerAdvice;
import org.springframework.web.method.annotation
    .HandlerMethodValidationException;
import org.springframework.web.method.annotation
    .MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes =
        KafkaDeadLetterCommandController.class
)
public class
KafkaDeadLetterCommandExceptionHandler {

    @ExceptionHandler(
        KafkaDeadLetterRecordNotFoundException.class
    )
    ResponseEntity<ApiError> handleNotFound(
        KafkaDeadLetterRecordNotFoundException
            exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.NOT_FOUND,
            exception.getCode(),
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(
        KafkaDeadLetterCommandException.class
    )
    ResponseEntity<ApiError> handleCommandFailure(
        KafkaDeadLetterCommandException exception,
        HttpServletRequest request
    ) {
        return response(
            statusOf(exception),
            exception.getCode(),
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(
        KafkaDeadLetterCommandAuditException.class
    )
    ResponseEntity<ApiError> handleAuditFailure(
        KafkaDeadLetterCommandAuditException
            exception,
        HttpServletRequest request
    ) {
        return response(
            auditStatusOf(exception),
            auditCodeOf(exception),
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(
        KafkaDeadLetterOperatorIdentityException.class
    )
    ResponseEntity<ApiError> handleInvalidOperatorIdentity(
        KafkaDeadLetterOperatorIdentityException
            exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.UNAUTHORIZED,
            KafkaDeadLetterOperatorIdentityException
                .CODE,
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler({
        HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> handleValidationFailure(
        Exception exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            "Request validation failed.",
            request
        );
    }

    private static HttpStatus auditStatusOf(
        KafkaDeadLetterCommandAuditException
            exception
    ) {
        return switch (exception.getReason()) {
            case ATTEMPT_PERSISTENCE_FAILED,
                 COMPLETION_PERSISTENCE_FAILED ->
                HttpStatus.SERVICE_UNAVAILABLE;

            case COMMAND_INTERNAL_FAILURE ->
                HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static String auditCodeOf(
        KafkaDeadLetterCommandAuditException
            exception
    ) {
        return switch (exception.getReason()) {
            case ATTEMPT_PERSISTENCE_FAILED ->
                "KAFKA_DEAD_LETTER_COMMAND_"
                    + "AUDIT_UNAVAILABLE";

            case COMPLETION_PERSISTENCE_FAILED ->
                "KAFKA_DEAD_LETTER_COMMAND_"
                    + "AUDIT_COMPLETION_UNAVAILABLE";

            case COMMAND_INTERNAL_FAILURE ->
                "KAFKA_DEAD_LETTER_COMMAND_"
                    + "INTERNAL_FAILURE";
        };
    }

    private static HttpStatus statusOf(
        KafkaDeadLetterCommandException
            exception
    ) {
        return switch (exception.getReason()) {
            case NOT_CLAIMABLE,
                 NOT_DISCARDABLE ->
                HttpStatus.CONFLICT;

            case REPLAY_FAILED ->
                HttpStatus.BAD_GATEWAY;

            case REPLAY_UNRESOLVED ->
                HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private static ResponseEntity<ApiError> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        ApiError body =
            new ApiError(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                List.of()
            );

        return ResponseEntity
            .status(status)
            .body(body);
    }
}
