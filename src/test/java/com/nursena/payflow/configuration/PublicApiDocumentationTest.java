package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import com.nursena.payflow.common.api.SystemController;
import com.nursena.payflow.user.adapter.in.web.AuthenticateUserController;
import com.nursena.payflow.user.adapter.in.web.AuthenticateUserRequest;
import com.nursena.payflow.user.adapter.in.web.EmailVerificationConfirmRequest;
import com.nursena.payflow.user.adapter.in.web.EmailVerificationController;
import com.nursena.payflow.user.adapter.in.web.EmailVerificationRequest;
import com.nursena.payflow.user.adapter.in.web.PasswordRecoveryConfirmRequest;
import com.nursena.payflow.user.adapter.in.web.PasswordRecoveryController;
import com.nursena.payflow.user.adapter.in.web.PasswordRecoveryRequest;
import com.nursena.payflow.user.adapter.in.web.RegisterUserController;
import com.nursena.payflow.user.adapter.in.web.RegisterUserRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;

class PublicApiDocumentationTest {

    @Test
    void shouldDocumentSystemHealthOperation()
        throws NoSuchMethodException {

        Method method =
            SystemController.class
                .getDeclaredMethod("health");

        assertDocumentation(
            SystemController.class,
            method,
            "System",
            "getSystemHealth",
            "200"
        );
    }

    @Test
    void shouldDocumentRegistrationOperation()
        throws NoSuchMethodException {

        Method method =
            RegisterUserController.class
                .getDeclaredMethod(
                    "register",
                    RegisterUserRequest.class
                );

        assertDocumentation(
            RegisterUserController.class,
            method,
            "Authentication",
            "registerUser",
            "201",
            "400",
            "409"
        );
    }

    @Test
    void shouldDocumentAuthenticationOperation()
        throws NoSuchMethodException {

        Method method =
            AuthenticateUserController.class
                .getDeclaredMethod(
                    "authenticate",
                    AuthenticateUserRequest.class,
                    HttpServletRequest.class
                );

        assertDocumentation(
            AuthenticateUserController.class,
            method,
            "Authentication",
            "authenticateUser",
            "200",
            "400",
            "401",
            "403",
            "429",
            "503"
        );
    }

    @Test
    void shouldDocumentEmailVerificationRequestOperation()
        throws NoSuchMethodException {

        Method method =
            EmailVerificationController.class
                .getDeclaredMethod(
                    "request",
                    EmailVerificationRequest.class
                );

        assertDocumentation(
            EmailVerificationController.class,
            method,
            "Authentication",
            "requestEmailVerification",
            "202",
            "400"
        );
    }

    @Test
    void shouldDocumentEmailVerificationConfirmationOperation()
        throws NoSuchMethodException {

        Method method =
            EmailVerificationController.class
                .getDeclaredMethod(
                    "confirm",
                    EmailVerificationConfirmRequest.class
                );

        assertDocumentation(
            EmailVerificationController.class,
            method,
            "Authentication",
            "confirmEmailVerification",
            "204",
            "400",
            "422"
        );
    }


    @Test
    void shouldDocumentPasswordRecoveryRequestOperation()
        throws NoSuchMethodException {

        Method method =
            PasswordRecoveryController.class
                .getDeclaredMethod(
                    "request",
                    PasswordRecoveryRequest.class
                );

        assertDocumentation(
            PasswordRecoveryController.class,
            method,
            "Authentication",
            "requestPasswordRecovery",
            "202",
            "400"
        );
    }

    @Test
    void shouldDocumentPasswordRecoveryConfirmationOperation()
        throws NoSuchMethodException {

        Method method =
            PasswordRecoveryController.class
                .getDeclaredMethod(
                    "confirm",
                    PasswordRecoveryConfirmRequest.class
                );

        assertDocumentation(
            PasswordRecoveryController.class,
            method,
            "Authentication",
            "confirmPasswordRecovery",
            "204",
            "400",
            "422"
        );
    }

    private static void assertDocumentation(
        Class<?> controllerType,
        Method method,
        String expectedTag,
        String expectedOperationId,
        String... expectedResponseCodes
    ) {
        Tag tag =
            controllerType.getAnnotation(Tag.class);

        assertThat(tag)
            .isNotNull();

        assertThat(tag.name())
            .isEqualTo(expectedTag);

        Operation operation =
            method.getAnnotation(Operation.class);

        assertThat(operation)
            .isNotNull();

        assertThat(operation.operationId())
            .isEqualTo(expectedOperationId);

        assertThat(operation.summary())
            .isNotBlank();

        assertThat(operation.security())
            .isEmpty();

        ApiResponses responses =
            method.getAnnotation(ApiResponses.class);

        assertThat(responses)
            .isNotNull();

        assertThat(
            Arrays.stream(responses.value())
                .map(ApiResponse::responseCode)
                .toList()
        )
            .containsExactlyInAnyOrder(
                expectedResponseCodes
            );
    }
}
