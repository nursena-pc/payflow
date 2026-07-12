package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.user.domain.exception.EmailAlreadyRegisteredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = RegisterUserController.class)
public class UserRegistrationExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<ApiError> handleEmailAlreadyRegistered(
        EmailAlreadyRegisteredException exception,
        HttpServletRequest request
    ) {
        ApiError body = new ApiError(
            Instant.now(),
            HttpStatus.CONFLICT.value(),
            exception.getCode(),
            exception.getMessage(),
            request.getRequestURI(),
            List.of()
        );

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(body);
    }
}
