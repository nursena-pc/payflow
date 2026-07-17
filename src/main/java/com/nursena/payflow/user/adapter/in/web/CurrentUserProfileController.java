package com.nursena.payflow.user.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.user.application.port.in.GetCurrentUserProfileResult;
import com.nursena.payflow.user.application.port.in.GetCurrentUserProfileUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiConfiguration;
import com.nursena.payflow.configuration.UserApiExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Users",
    description = "Authenticated user profile operations."
)

@RestController
@RequestMapping("/api/v1/users")
public class CurrentUserProfileController {

    private final GetCurrentUserProfileUseCase profileUseCase;

    public CurrentUserProfileController(
        GetCurrentUserProfileUseCase profileUseCase
    ) {
        this.profileUseCase = profileUseCase;
    }

    @Operation(
        operationId = "getCurrentUserProfile",
        summary = "Get current user profile",
        description =
            "Returns the profile represented by the JWT subject."
    )
    @SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Current user profile returned.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        CurrentUserProfileResponse.class
                ),
                examples = @ExampleObject(
                    value =
                        UserApiExamples.CURRENT_USER_PROFILE
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description =
                "Bearer token is missing or invalid."
        ),
        @ApiResponse(
            responseCode = "404",
            description =
                "The JWT subject does not reference an existing user.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        UserApiExamples.USER_NOT_FOUND
                )
            )
        )
    })
    @GetMapping("/me")
    public ResponseEntity<CurrentUserProfileResponse>
    getCurrentUserProfile(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        Jwt jwt
    ) {
        UUID userId = UUID.fromString(
            jwt.getSubject()
        );

        GetCurrentUserProfileResult result =
            profileUseCase.getProfile(userId);

        return ResponseEntity.ok(
            CurrentUserProfileResponse.from(result)
        );
    }
}
