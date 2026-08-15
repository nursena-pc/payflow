package com.nursena.payflow.user.adapter.in.web;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Objects;

import com.nursena.payflow.clientcontext.adapter.in.web.ClientAddressResolver;
import com.nursena.payflow.clientcontext.domain.ResolvedClientAddress;
import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.user.application.port.in.AuthenticatedUserResult;
import com.nursena.payflow.user.application.port.in.ConfirmMfaLoginChallengeCommand;
import com.nursena.payflow.user.application.port.in.ConfirmMfaLoginChallengeUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth/mfa/challenges")
public class ConfirmMfaLoginChallengeController {

    private static final String TOKEN_TYPE = "Bearer";
    private final ConfirmMfaLoginChallengeUseCase useCase;
    private final ClientAddressResolver clientAddressResolver;

    public ConfirmMfaLoginChallengeController(
        ConfirmMfaLoginChallengeUseCase useCase,
        ClientAddressResolver clientAddressResolver
    ) {
        this.useCase = Objects.requireNonNull(
            useCase,
            "useCase must not be null"
        );
        this.clientAddressResolver = Objects.requireNonNull(
            clientAddressResolver,
            "clientAddressResolver must not be null"
        );
    }

    @Operation(
        operationId = "confirmMfaLoginChallenge",
        summary = "Complete an MFA login challenge",
        description =
            "Consumes one pending challenge after a valid TOTP or unused "
                + "recovery-code proof and only then issues access and refresh "
                + "credentials."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "MFA challenge completed.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = AuthenticateUserResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Challenge or MFA proof is invalid.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        ),
        @ApiResponse(
            responseCode = "503",
            description = "MFA secret protection is unavailable.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        )
    })
    @PostMapping("/confirm")
    public ResponseEntity<AuthenticateUserResponse> confirm(
        @RequestBody(required = false) ConfirmMfaLoginChallengeRequest request,
        @Parameter(hidden = true) HttpServletRequest servletRequest
    ) {
        ResolvedClientAddress clientAddress =
            clientAddressResolver.resolve(servletRequest);

        AuthenticatedUserResult result = useCase.confirm(
            new ConfirmMfaLoginChallengeCommand(
                request == null ? null : request.challengeToken(),
                request == null ? null : request.code(),
                clientAddress.address()
            )
        );
        return ResponseEntity.ok(toResponse(result));
    }

    static AuthenticateUserResponse toResponse(AuthenticatedUserResult result) {
        return new AuthenticateUserResponse(
            result.accessToken(),
            TOKEN_TYPE,
            result.expiresAt(),
            result.refreshToken(),
            result.refreshTokenExpiresAt()
        );
    }
}
