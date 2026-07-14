package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes =
        CurrentUserProfileController.class
)
public class CurrentUserProfileExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiError> handleUserNotFound(
        UserNotFoundException exception,
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
