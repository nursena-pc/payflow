package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.observability.adapter.in.web
    .RequestCorrelationContext;
import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation
    .ExceptionHandler;
import org.springframework.web.bind.annotation
    .RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes = PasswordRecoveryController.class
)
public class PasswordRecoveryExceptionHandler {

    @ExceptionHandler(
        InvalidAccountActionCredentialException.class
    )
    ResponseEntity<ApiError> handleInvalidCredential(
        InvalidAccountActionCredentialException exception,
        HttpServletRequest request
    ) {
        HttpStatus status =
            HttpStatus.UNPROCESSABLE_ENTITY;

        ApiError body = new ApiError(
            Instant.now(),
            status.value(),
            exception.getCode(),
            exception.getMessage(),
            request.getRequestURI(),
            RequestCorrelationContext.require(request),
            List.of()
        );

        return ResponseEntity
            .status(status)
            .body(body);
    }
}
