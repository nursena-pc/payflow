
package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.user.application.exception
    .LoginRateLimitExceededException;
import com.nursena.payflow.user.application.exception
    .LoginRateLimitUnavailableException;
import com.nursena.payflow.user.domain.exception
    .InvalidCredentialsException;
import com.nursena.payflow.user.domain.exception
    .InvalidRefreshTokenException;
import com.nursena.payflow.user.domain.exception
    .UserAccountUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation
    .ExceptionHandler;
import org.springframework.web.bind.annotation
    .RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes = {
        AuthenticateUserController.class,
        RotateRefreshCredentialsController.class
    }
)
public class UserAuthenticationExceptionHandler {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            UserAuthenticationExceptionHandler.class
        );

    @ExceptionHandler(
        InvalidCredentialsException.class
    )
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

    @ExceptionHandler(
        UserAccountUnavailableException.class
    )
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

    @ExceptionHandler(
        LoginRateLimitExceededException.class
    )
    ResponseEntity<ApiError> handleRateLimitExceeded(
        LoginRateLimitExceededException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn(
            "security_event=login_rate_limit_blocked "
                + "dimension={} path={}",
            exception
                .getBlockedDimension()
                .name()
                .toLowerCase(
                    Locale.ROOT
                ),
            request.getRequestURI()
        );

        return ResponseEntity
            .status(
                HttpStatus.TOO_MANY_REQUESTS
            )
            .header(
                HttpHeaders.RETRY_AFTER,
                Long.toString(
                    exception
                        .getRetryAfter()
                        .toSeconds()
                )
            )
            .body(
                errorBody(
                    HttpStatus.TOO_MANY_REQUESTS,
                    exception.getCode(),
                    exception.getMessage(),
                    request
                )
            );
    }

    @ExceptionHandler(
        LoginRateLimitUnavailableException.class
    )
    ResponseEntity<ApiError> handleRateLimitUnavailable(
        LoginRateLimitUnavailableException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn(
            "security_event=login_rate_limit_unavailable "
                + "path={}",
            request.getRequestURI()
        );

        return buildResponse(
            HttpStatus.SERVICE_UNAVAILABLE,
            exception.getCode(),
            exception.getMessage(),
            request
        );
    }

    private static ResponseEntity<ApiError>
    buildResponse(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        return ResponseEntity
            .status(status)
            .body(
                errorBody(
                    status,
                    code,
                    message,
                    request
                )
            );
    }

    private static ApiError errorBody(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request
    ) {
        return new ApiError(
            Instant.now(),
            status.value(),
            code,
            message,
            request.getRequestURI(),
            List.of()
        );
    }
}
