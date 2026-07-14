package com.nursena.payflow.wallet.adapter.in.web;

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

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes = GetCurrentWalletController.class
)
public class GetCurrentWalletExceptionHandler {

    @ExceptionHandler(WalletNotFoundException.class)
    ResponseEntity<ApiError> handleWalletNotFound(
        WalletNotFoundException exception,
        HttpServletRequest request
    ) {
        ApiError body = new ApiError(
            Instant.now(),
            HttpStatus.NOT_FOUND.value(),
            exception.getCode(),
            exception.getMessage(),
            request.getRequestURI(),
            List.of()
        );

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(body);
    }
}
