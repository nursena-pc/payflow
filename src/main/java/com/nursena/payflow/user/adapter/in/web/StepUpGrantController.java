package com.nursena.payflow.user.adapter.in.web;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.UUID;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiConfiguration;
import com.nursena.payflow.user.application.port.in.IssueStepUpGrantCommand;
import com.nursena.payflow.user.application.port.in.IssueStepUpGrantResult;
import com.nursena.payflow.user.application.port.in.IssueStepUpGrantUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Users",
    description = "Authenticated user identity and security operations."
)
@RestController
@RequestMapping("/api/v1/users/me/step-up/grants")
public class StepUpGrantController {

    private final IssueStepUpGrantUseCase issueUseCase;

    public StepUpGrantController(IssueStepUpGrantUseCase issueUseCase) {
        this.issueUseCase = issueUseCase;
    }

    @Operation(
        operationId = "issueStepUpGrant",
        summary = "Issue a purpose-bound step-up grant",
        description =
            "Verifies the enabled MFA factor for the authenticated subject "
                + "and returns one short-lived opaque grant for the exact purpose."
    )
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH_SCHEME)
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Step-up grant issued.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = StepUpGrantResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "Request validation or step-up purpose failed."),
        @ApiResponse(responseCode = "401", description = "The supplied second-factor proof could not be verified."),
        @ApiResponse(
            responseCode = "403",
            description = "The account or purpose cannot authorize this step-up request.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        ),
        @ApiResponse(responseCode = "404", description = "The authenticated subject no longer exists."),
        @ApiResponse(responseCode = "409", description = "Enabled MFA is not available for step-up."),
        @ApiResponse(responseCode = "503", description = "MFA security infrastructure cannot make a safe decision.")
    })
    @PostMapping
    public ResponseEntity<StepUpGrantResponse> issue(
        @Parameter(hidden = true)
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody IssueStepUpGrantRequest request
    ) {
        IssueStepUpGrantResult result = issueUseCase.issue(
            new IssueStepUpGrantCommand(
                UUID.fromString(jwt.getSubject()),
                request.purpose(),
                request.code()
            )
        );
        return ResponseEntity.ok(StepUpGrantResponse.from(result));
    }
}
