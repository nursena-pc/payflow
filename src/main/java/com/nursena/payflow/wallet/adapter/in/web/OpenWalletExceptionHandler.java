package com.nursena.payflow.wallet.adapter.in.web;

import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationContext;
import java.time.Instant;
import java.util.List;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.wallet.domain.exception.WalletAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes = OpenWalletController.class
)
public class OpenWalletExceptionHandler {

    @ExceptionHandler(WalletAlreadyExistsException.class)
    ResponseEntity<ApiError> handleWalletAlreadyExists(
        WalletAlreadyExistsException exception,
        HttpServletRequest request
    ) {
        ApiError body = new ApiError(
            Instant.now(),
            HttpStatus.CONFLICT.value(),
            exception.getCode(),
            exception.getMessage(),
            request.getRequestURI(),
            RequestCorrelationContext.require(request),
            List.of()
        );

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(body);
    }
}
