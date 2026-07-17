package com.nursena.payflow.user.adapter.in.web;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiExamples;
import com.nursena.payflow.user.application.port.in.RegisterUserCommand;
import com.nursena.payflow.user.application.port.in.RegisterUserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(
    name = "Authentication",
    description =
        "Public user registration and authentication operations."
)

@RestController
@RequestMapping("/api/v1/auth")
public class RegisterUserController {

    private final RegisterUserUseCase registerUserUseCase;

    public RegisterUserController(RegisterUserUseCase registerUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
    }

    @Operation(
        operationId = "registerUser",
        summary = "Register a user",
        description =
            "Creates a user with a normalized email address "
                + "and BCrypt password hash."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "User registered.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        RegisterUserResponse.class
                ),
                examples = @ExampleObject(
                    value =
                        OpenApiExamples.REGISTER_SUCCESS
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
                            .REGISTER_VALIDATION_ERROR
                )
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description =
                "The email address is already registered.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        OpenApiExamples
                            .EMAIL_ALREADY_REGISTERED
                )
            )
        )
    })

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> register(
        @Valid @RequestBody RegisterUserRequest request
    ) {
        RegisterUserCommand command = new RegisterUserCommand(
            request.email(),
            request.password()
        );

        var userId = registerUserUseCase.register(command);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(new RegisterUserResponse(userId));
    }
}
