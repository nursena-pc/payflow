
package com.nursena.payflow.user.adapter.in.web;

import static org.springframework.http.MediaType
    .APPLICATION_JSON_VALUE;

import java.util.Objects;
import com.nursena.payflow.clientcontext.adapter.in.web
    .ClientAddressResolver;
import com.nursena.payflow.clientcontext.domain
    .ResolvedClientAddress;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiExamples;
import com.nursena.payflow.user.application.port.in
    .AuthenticateUserCommand;
import com.nursena.payflow.user.application.port.in
    .AuthenticateUserResult;
import com.nursena.payflow.user.application.port.in
    .AuthenticateUserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses
    .ApiResponse;
import io.swagger.v3.oas.annotations.responses
    .ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/auth")
public class AuthenticateUserController {

    private static final String TOKEN_TYPE =
        "Bearer";

    private final AuthenticateUserUseCase
        authenticateUserUseCase;

    private final ClientAddressResolver
        clientAddressResolver;

    public AuthenticateUserController(
        AuthenticateUserUseCase authenticateUserUseCase,
        ClientAddressResolver clientAddressResolver
    ) {
        this.authenticateUserUseCase =
            Objects.requireNonNull(
                authenticateUserUseCase,
                "authenticateUserUseCase "
                    + "must not be null"
            );

        this.clientAddressResolver =
            Objects.requireNonNull(
                clientAddressResolver,
                "clientAddressResolver "
                    + "must not be null"
            );
    }

    @Operation(
        operationId = "authenticateUser",
        summary = "Authenticate a user",
        description =
            "Applies distributed login protection, "
                + "validates user credentials, and "
                + "returns an RSA-signed JWT access "
                + "token with an opaque refresh token."
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
                "The user account cannot "
                    + "be authenticated.",
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
        ),
        @ApiResponse(
            responseCode = "429",
            description =
                "The login rate limit was exceeded.",
            headers = @Header(
                name = "Retry-After",
                description =
                    "Whole seconds until another "
                        + "login attempt may be made.",
                schema = @Schema(
                    type = "integer",
                    format = "int64",
                    minimum = "1"
                )
            ),
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        OpenApiExamples
                            .LOGIN_RATE_LIMIT_EXCEEDED
                )
            )
        ),
        @ApiResponse(
            responseCode = "503",
            description =
                "Login protection is temporarily "
                    + "unavailable.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        OpenApiExamples
                            .LOGIN_RATE_LIMIT_UNAVAILABLE
                )
            )
        )
    })
    @PostMapping("/login")
    public ResponseEntity<AuthenticateUserResponse>
    authenticate(
        @Valid @RequestBody
        AuthenticateUserRequest request,
        @Parameter(hidden = true)
        HttpServletRequest servletRequest
    ) {
        ResolvedClientAddress clientAddress =
            clientAddressResolver.resolve(
                servletRequest
            );

        AuthenticateUserCommand command =
            new AuthenticateUserCommand(
                request.email(),
                request.password(),
                clientAddress.address().value()
            );

        AuthenticateUserResult result =
            authenticateUserUseCase.authenticate(
                command
            );

        AuthenticateUserResponse response =
            new AuthenticateUserResponse(
                result.accessToken(),
                TOKEN_TYPE,
                result.expiresAt(),
                result.refreshToken(),
                result.refreshTokenExpiresAt()
            );

        return ResponseEntity.ok(response);
    }
}
