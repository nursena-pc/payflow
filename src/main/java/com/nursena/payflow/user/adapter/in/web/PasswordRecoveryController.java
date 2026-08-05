package com.nursena.payflow.user.adapter.in.web;

import static org.springframework.http.MediaType
    .APPLICATION_JSON_VALUE;

import java.util.Objects;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiExamples;
import com.nursena.payflow.user.application.port.in
    .ConfirmPasswordRecoveryCommand;
import com.nursena.payflow.user.application.port.in
    .ConfirmPasswordRecoveryUseCase;
import com.nursena.payflow.user.application.port.in
    .RequestPasswordRecoveryCommand;
import com.nursena.payflow.user.application.port.in
    .RequestPasswordRecoveryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses
    .ApiResponse;
import io.swagger.v3.oas.annotations.responses
    .ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation
    .RequestBody;
import org.springframework.web.bind.annotation
    .RequestMapping;
import org.springframework.web.bind.annotation
    .RestController;

@Tag(
    name = "Authentication",
    description =
        "Public user registration and "
            + "authentication operations."
)
@RestController
@RequestMapping("/api/v1/auth/password-recovery")
public class PasswordRecoveryController {

    private final RequestPasswordRecoveryUseCase
        requestPasswordRecovery;
    private final ConfirmPasswordRecoveryUseCase
        confirmPasswordRecovery;

    public PasswordRecoveryController(
        RequestPasswordRecoveryUseCase
            requestPasswordRecovery,
        ConfirmPasswordRecoveryUseCase
            confirmPasswordRecovery
    ) {
        this.requestPasswordRecovery =
            Objects.requireNonNull(
                requestPasswordRecovery,
                "requestPasswordRecovery must not be null"
            );
        this.confirmPasswordRecovery =
            Objects.requireNonNull(
                confirmPasswordRecovery,
                "confirmPasswordRecovery must not be null"
            );
    }

    @Operation(
        operationId = "requestPasswordRecovery",
        summary = "Request password recovery",
        description =
            "Accepts every valid email-shaped identity "
                + "without disclosing account existence "
                + "or recovery eligibility."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "202",
            description =
                "The password-recovery request was accepted."
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Request validation failed.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value = OpenApiExamples
                        .PASSWORD_RECOVERY_REQUEST_VALIDATION_ERROR
                )
            )
        )
    })
    @PostMapping("/requests")
    public ResponseEntity<Void> request(
        @Valid @RequestBody
        PasswordRecoveryRequest request
    ) {
        requestPasswordRecovery.request(
            new RequestPasswordRecoveryCommand(
                request.email()
            )
        );

        return ResponseEntity.accepted().build();
    }

    @Operation(
        operationId = "confirmPasswordRecovery",
        summary = "Confirm password recovery",
        description =
            "Consumes one opaque, time-limited credential, "
                + "replaces the BCrypt password hash, and "
                + "revokes every active refresh session."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description =
                "The password was replaced and active "
                    + "refresh sessions were revoked."
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Request validation failed.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value = OpenApiExamples
                        .PASSWORD_RECOVERY_CONFIRM_VALIDATION_ERROR
                )
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description =
                "The credential is invalid or no longer usable.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value = OpenApiExamples
                        .INVALID_PASSWORD_RECOVERY_CREDENTIAL
                )
            )
        )
    })
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(
        @Valid @RequestBody
        PasswordRecoveryConfirmRequest request
    ) {
        confirmPasswordRecovery.confirm(
            new ConfirmPasswordRecoveryCommand(
                request.credential(),
                request.newPassword()
            )
        );

        return ResponseEntity.noContent().build();
    }
}
