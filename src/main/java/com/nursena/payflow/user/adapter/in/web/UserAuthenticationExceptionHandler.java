package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.user.domain.exception.InvalidCredentialsException;
import com.nursena.payflow.user.domain.exception.InvalidRefreshTokenException;
import com.nursena.payflow.user.domain.exception.UserAccountUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes = {
        AuthenticateUserController.class,
        RotateRefreshCredentialsController.class
    }
)
public class UserAuthenticationExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> handleInvalidCredentials(
        InvalidCredentialsException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.UNAUTHORIZED,
            exception.getCode(),
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(UserAccountUnavailableException.class)
    ResponseEntity<ApiError> handleUnavailableAccount(
        UserAccountUnavailableException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.FORBIDDEN,
            exception.getCode(),
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(
        InvalidRefreshTokenException.class
    )
    ResponseEntity<ApiError> handleInvalidRefreshToken(
        InvalidRefreshTokenException exception,
        HttpServletRequest request
    ) {
        return buildResponse(
            HttpStatus.UNAUTHORIZED,
            exception.getCode(),
            exception.getMessage(),
            request
        );
    }

    private ResponseEntity<ApiError> buildResponse(
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
