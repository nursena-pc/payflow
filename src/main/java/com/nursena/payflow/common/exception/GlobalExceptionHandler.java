package com.nursena.payflow.common.exception;

import java.time.Instant;
import java.util.List;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationContext;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> handleBusinessRule(
        BusinessRuleException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.UNPROCESSABLE_ENTITY,
            exception.getCode(),
            exception.getMessage(),
            request,
            List.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        List<ApiError.FieldViolation> violations =
            exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(
                    error ->
                        new ApiError.FieldViolation(
                            error.getField(),
                            error.getDefaultMessage()
                        )
                )
                .toList();

        return buildResponse(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            "Request validation failed.",
            request,
            violations
        );
    }

    private ResponseEntity<ApiError> buildResponse(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request,
        List<ApiError.FieldViolation> violations
    ) {
        ApiError body =
            new ApiError(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                RequestCorrelationContext.require(request),
                violations
            );

        return ResponseEntity
            .status(status)
            .body(body);
    }
}