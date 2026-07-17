package com.nursena.payflow.user.adapter.in.web;

import com.nursena.payflow.user.application.port.in.AuthenticateUserCommand;
import com.nursena.payflow.user.application.port.in.AuthenticateUserResult;
import com.nursena.payflow.user.application.port.in.AuthenticateUserUseCase;
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
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiExamples;

@Tag(
    name = "Authentication",
    description =
        "Public user registration and authentication operations."
)

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticateUserController {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticateUserUseCase authenticateUserUseCase;

    public AuthenticateUserController(
        AuthenticateUserUseCase authenticateUserUseCase
    ) {
        this.authenticateUserUseCase = authenticateUserUseCase;
    }

    @Operation(
        operationId = "authenticateUser",
        summary = "Authenticate a user",
        description =
            "Validates user credentials and returns an "
                + "RSA-signed JWT access token."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Authentication succeeded.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        AuthenticateUserResponse.class
                ),
                examples = @ExampleObject(
                    value =
                        OpenApiExamples.LOGIN_SUCCESS
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
                            .LOGIN_VALIDATION_ERROR
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Credentials are invalid.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        OpenApiExamples.INVALID_CREDENTIALS
                )
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description =
                "The user account cannot be authenticated.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        OpenApiExamples
                            .USER_ACCOUNT_UNAVAILABLE
                )
            )
        )
    })

    @PostMapping("/login")
    public ResponseEntity<AuthenticateUserResponse> authenticate(
        @Valid @RequestBody AuthenticateUserRequest request
    ) {
        AuthenticateUserCommand command =
            new AuthenticateUserCommand(
                request.email(),
                request.password()
            );

        AuthenticateUserResult result =
            authenticateUserUseCase.authenticate(command);

        AuthenticateUserResponse response =
            new AuthenticateUserResponse(
                result.accessToken(),
                TOKEN_TYPE,
                result.expiresAt()
            );

        return ResponseEntity.ok(response);
    }
}
