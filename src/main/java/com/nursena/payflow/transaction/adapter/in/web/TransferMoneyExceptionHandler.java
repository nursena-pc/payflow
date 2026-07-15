package com.nursena.payflow.transaction.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.transaction.domain.exception.IdempotencyConflictException;
import com.nursena.payflow.transaction.domain.exception.IdempotencyRequestInProgressException;
import com.nursena.payflow.wallet.domain.exception.WalletConcurrentUpdateException;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes = TransferMoneyController.class
)
public class TransferMoneyExceptionHandler {

    @ExceptionHandler(WalletNotFoundException.class)
    ResponseEntity<ApiError> handleWalletNotFound(
        WalletNotFoundException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.NOT_FOUND,
            exception.getCode(),
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler({
        IdempotencyConflictException.class,
        IdempotencyRequestInProgressException.class,
        WalletConcurrentUpdateException.class
    })
    ResponseEntity<ApiError> handleConflict(
        RuntimeException exception,
        HttpServletRequest request
    ) {
        String code;
        String message;

        if (exception
            instanceof IdempotencyConflictException conflict) {
            code = conflict.getCode();
            message = conflict.getMessage();
        } else if (
            exception
                instanceof IdempotencyRequestInProgressException
                inProgress
        ) {
            code = inProgress.getCode();
            message = inProgress.getMessage();
        } else {
            WalletConcurrentUpdateException concurrentUpdate =
                (WalletConcurrentUpdateException) exception;

            code = concurrentUpdate.getCode();
            message = concurrentUpdate.getMessage();
        }

        return buildResponse(
            HttpStatus.CONFLICT,
            code,
            message,
            request
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> handleMissingHeader(
        MissingRequestHeaderException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            "MISSING_IDEMPOTENCY_KEY",
            "Idempotency-Key header is required.",
            request
        );
    }

    private static ResponseEntity<ApiError> buildResponse(
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
