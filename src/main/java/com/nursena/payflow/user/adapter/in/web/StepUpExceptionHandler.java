package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.common.exception.BusinessRuleException;
import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationContext;
import com.nursena.payflow.user.application.exception.MfaSecurityUnavailableException;
import com.nursena.payflow.user.domain.exception.InvalidStepUpGrantException;
import com.nursena.payflow.user.domain.exception.InvalidStepUpPurposeException;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.exception.MfaVerificationFailedException;
import com.nursena.payflow.user.domain.exception.StepUpRequiredException;
import com.nursena.payflow.user.domain.exception.UserAccountUnavailableException;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = StepUpGrantController.class)
public class StepUpExceptionHandler {

    @ExceptionHandler(InvalidStepUpPurposeException.class)
    ResponseEntity<ApiError> handlePurpose(
        InvalidStepUpPurposeException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, exception, request);
    }

    @ExceptionHandler(MfaVerificationFailedException.class)
    ResponseEntity<ApiError> handleVerification(
        MfaVerificationFailedException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.UNAUTHORIZED, exception, request);
    }

    @ExceptionHandler({
        InvalidStepUpGrantException.class,
        StepUpRequiredException.class,
        UserAccountUnavailableException.class
    })
    ResponseEntity<ApiError> handleForbidden(
        BusinessRuleException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.FORBIDDEN, exception, request);
    }

    @ExceptionHandler(MfaStateConflictException.class)
    ResponseEntity<ApiError> handleState(
        MfaStateConflictException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, exception, request);
    }

    @ExceptionHandler(MfaSecurityUnavailableException.class)
    ResponseEntity<ApiError> handleUnavailable(
        MfaSecurityUnavailableException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception, request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiError> handleMissingUser(
        UserNotFoundException exception,
        HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, exception, request);
    }

    private static ResponseEntity<ApiError> response(
        HttpStatus status,
        BusinessRuleException exception,
        HttpServletRequest request
    ) {
        ApiError body = new ApiError(
            Instant.now(),
            status.value(),
            exception.getCode(),
            exception.getMessage(),
            request.getRequestURI(),
            RequestCorrelationContext.require(request),
            List.of()
        );
        return ResponseEntity.status(status).body(body);
    }
}
