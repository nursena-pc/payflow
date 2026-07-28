package com.nursena.payflow.user.adapter.in.web;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiExamples;
import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsCommand;
import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsResult;
import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsUseCase;
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
public class RotateRefreshCredentialsController {

    private static final String TOKEN_TYPE =
        "Bearer";

    private final RotateRefreshCredentialsUseCase
        rotateRefreshCredentialsUseCase;

    public RotateRefreshCredentialsController(
        RotateRefreshCredentialsUseCase
            rotateRefreshCredentialsUseCase
    ) {
        this.rotateRefreshCredentialsUseCase =
            rotateRefreshCredentialsUseCase;
    }

    @Operation(
        operationId = "rotateRefreshCredentials",
        summary = "Rotate refresh credentials",
        description =
            "Consumes an active opaque refresh token "
                + "and returns a new access and refresh "
                + "credential pair."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description =
                "Refresh credentials rotated successfully.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        RotateRefreshCredentialsResponse.class
                ),
                examples = @ExampleObject(
                    value =
                        OpenApiExamples.REFRESH_SUCCESS
                )
            )
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
                            .REFRESH_VALIDATION_ERROR
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description =
                "The refresh credential is invalid.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        OpenApiExamples
                            .INVALID_REFRESH_TOKEN
                )
            )
        )
    })
    @PostMapping("/refresh")
    public ResponseEntity<
        RotateRefreshCredentialsResponse
    > rotate(
        @Valid @RequestBody
        RotateRefreshCredentialsRequest request
    ) {
        RotateRefreshCredentialsCommand command =
            new RotateRefreshCredentialsCommand(
                request.refreshToken()
            );

        RotateRefreshCredentialsResult result =
            rotateRefreshCredentialsUseCase.rotate(
                command
            );

        RotateRefreshCredentialsResponse response =
            new RotateRefreshCredentialsResponse(
                result.accessToken(),
                TOKEN_TYPE,
                result.expiresAt(),
                result.refreshToken(),
                result.refreshTokenExpiresAt()
            );

        return ResponseEntity.ok(response);
    }
}
