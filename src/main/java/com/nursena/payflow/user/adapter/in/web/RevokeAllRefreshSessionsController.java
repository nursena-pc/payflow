package com.nursena.payflow.user.adapter.in.web;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.configuration.OpenApiConfiguration;
import com.nursena.payflow.user.application.port.in.RevokeAllRefreshSessionsCommand;
import com.nursena.payflow.user.application.port.in.RevokeAllRefreshSessionsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Authentication",
    description =
        "Public and authenticated user-session operations."
)
@RestController
@RequestMapping("/api/v1/auth")
public class RevokeAllRefreshSessionsController {

    private final RevokeAllRefreshSessionsUseCase
        revokeAllRefreshSessionsUseCase;

    public RevokeAllRefreshSessionsController(
        RevokeAllRefreshSessionsUseCase
            revokeAllRefreshSessionsUseCase
    ) {
        this.revokeAllRefreshSessionsUseCase =
            Objects.requireNonNull(
                revokeAllRefreshSessionsUseCase,
                "revokeAllRefreshSessionsUseCase "
                    + "must not be null"
            );
    }

    @Operation(
        operationId = "revokeAllRefreshSessions",
        summary = "Log out every refresh session",
        description =
            "Revokes every active refresh-token family "
                + "owned by the authenticated user. "
                + "Existing access tokens remain valid "
                + "until their normal expiration."
    )
    @SecurityRequirement(
        name =
            OpenApiConfiguration
                .BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description =
                "All active refresh sessions were "
                    + "revoked, or no active session "
                    + "required mutation."
        ),
        @ApiResponse(
            responseCode = "401",
            description =
                "A valid access token is required."
        ),
        @ApiResponse(
            responseCode = "500",
            description =
                "The revocation transaction could not "
                    + "be completed."
        )
    })
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        Jwt jwt
    ) {
        UUID userId =
            authenticatedUserId(jwt);

        revokeAllRefreshSessionsUseCase.revoke(
            new RevokeAllRefreshSessionsCommand(
                userId
            )
        );

        return ResponseEntity
            .noContent()
            .build();
    }

    private static UUID authenticatedUserId(
        Jwt jwt
    ) {
        Jwt checkedJwt =
            Objects.requireNonNull(
                jwt,
                "jwt must not be null"
            );

        String subject =
            Objects.requireNonNull(
                checkedJwt.getSubject(),
                "jwt subject must not be null"
            );

        return UUID.fromString(subject);
    }
}
