package com.nursena.payflow.user.adapter.in.web;

import static org.springframework.http.MediaType
    .APPLICATION_JSON_VALUE;

import java.util.Objects;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiExamples;
import com.nursena.payflow.user.application.port.in
    .ConfirmEmailVerificationCommand;
import com.nursena.payflow.user.application.port.in
    .ConfirmEmailVerificationUseCase;
import com.nursena.payflow.user.application.port.in
    .RequestEmailVerificationCommand;
import com.nursena.payflow.user.application.port.in
    .RequestEmailVerificationUseCase;
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
@RequestMapping("/api/v1/auth/email-verification")
public class EmailVerificationController {

    private final RequestEmailVerificationUseCase
        requestEmailVerification;
    private final ConfirmEmailVerificationUseCase
        confirmEmailVerification;

    public EmailVerificationController(
        RequestEmailVerificationUseCase
            requestEmailVerification,
        ConfirmEmailVerificationUseCase
            confirmEmailVerification
    ) {
        this.requestEmailVerification =
            Objects.requireNonNull(
                requestEmailVerification,
                "requestEmailVerification "
                    + "must not be null"
            );
        this.confirmEmailVerification =
            Objects.requireNonNull(
                confirmEmailVerification,
                "confirmEmailVerification "
                    + "must not be null"
            );
    }

    @Operation(
        operationId = "requestEmailVerification",
        summary = "Request email verification",
        description =
            "Accepts every valid email-shaped identity "
                + "without disclosing account existence "
                + "or verification eligibility."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "202",
            description =
                "The verification request was accepted."
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
                        .EMAIL_VERIFICATION_REQUEST_VALIDATION_ERROR
                )
            )
        )
    })
    @PostMapping("/requests")
    public ResponseEntity<Void> request(
        @Valid @RequestBody
        EmailVerificationRequest request
    ) {
        requestEmailVerification.request(
            new RequestEmailVerificationCommand(
                request.email()
            )
        );

        return ResponseEntity.accepted().build();
    }

    @Operation(
        operationId = "confirmEmailVerification",
        summary = "Confirm email verification",
        description =
            "Consumes one opaque, time-limited credential "
                + "and marks email ownership exactly once."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description = "Email ownership was confirmed."
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
                        .EMAIL_VERIFICATION_CONFIRM_VALIDATION_ERROR
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
                        .INVALID_ACCOUNT_ACTION_CREDENTIAL
                )
            )
        )
    })
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(
        @Valid @RequestBody
        EmailVerificationConfirmRequest request
    ) {
        confirmEmailVerification.confirm(
            new ConfirmEmailVerificationCommand(
                request.credential()
            )
        );

        return ResponseEntity.noContent().build();
    }
}
