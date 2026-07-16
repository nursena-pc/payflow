package com.nursena.payflow.transaction.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes =
        GetTransactionHistoryController.class
)
public class TransactionHistoryExceptionHandler {

    @ExceptionHandler(WalletNotFoundException.class)
    ResponseEntity<ApiError> handleWalletNotFound(
        WalletNotFoundException exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.NOT_FOUND,
            exception.getCode(),
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler({
        HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> handleInvalidPagination(
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

    private static ResponseEntity<ApiError> response(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        ApiError body = new ApiError(
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
