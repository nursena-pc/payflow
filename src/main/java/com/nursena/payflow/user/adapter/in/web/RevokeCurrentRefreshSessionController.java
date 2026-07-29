package com.nursena.payflow.user.adapter.in.web;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiExamples;
import com.nursena.payflow.user.application.port.in.RevokeCurrentRefreshSessionCommand;
import com.nursena.payflow.user.application.port.in.RevokeCurrentRefreshSessionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Authentication",
    description =
        "Public user registration and authentication operations."
)
@RestController
@RequestMapping("/api/v1/auth")
public class RevokeCurrentRefreshSessionController {

    private final RevokeCurrentRefreshSessionUseCase
        revokeCurrentRefreshSessionUseCase;

    public RevokeCurrentRefreshSessionController(
        RevokeCurrentRefreshSessionUseCase
            revokeCurrentRefreshSessionUseCase
    ) {
        this.revokeCurrentRefreshSessionUseCase =
            revokeCurrentRefreshSessionUseCase;
    }

    @Operation(
        operationId = "revokeCurrentRefreshSession",
        summary = "Log out the current refresh session",
        description =
            "Revokes the refresh-token family represented "
                + "by the submitted opaque credential. "
                + "The response does not reveal whether "
                + "the credential exists or remains active."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description =
                "The logout request was processed without "
                    + "exposing refresh-token state."
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
                    value =
                        OpenApiExamples
                            .LOGOUT_VALIDATION_ERROR
                )
            )
        )
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @Valid @RequestBody
        RevokeCurrentRefreshSessionRequest request
    ) {
        RevokeCurrentRefreshSessionCommand command =
            new RevokeCurrentRefreshSessionCommand(
                request.refreshToken()
            );

        revokeCurrentRefreshSessionUseCase.revoke(
            command
        );

        return ResponseEntity.noContent().build();
    }
}
