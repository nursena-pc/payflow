package com.nursena.payflow.wallet.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiConfiguration;
import com.nursena.payflow.configuration.WalletApiExamples;
import com.nursena.payflow.wallet.application.port.in.GetCurrentWalletResult;
import com.nursena.payflow.wallet.application.port.in.GetCurrentWalletUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(
    name = "Wallets",
    description = "Authenticated wallet management operations."
)

@RestController
@RequestMapping("/api/v1/wallets")
public class GetCurrentWalletController {

    private final GetCurrentWalletUseCase getCurrentWalletUseCase;

    public GetCurrentWalletController(
        GetCurrentWalletUseCase getCurrentWalletUseCase
    ) {
        this.getCurrentWalletUseCase =
            getCurrentWalletUseCase;
    }

    @Operation(
        operationId = "getCurrentWallet",
        summary = "Get current wallet",
        description =
            "Returns the wallet owned by the authenticated user."
    )
    @SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Current wallet returned.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        GetCurrentWalletResponse.class
                ),
                examples = @ExampleObject(
                    value =
                        WalletApiExamples.CURRENT_WALLET
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
                "The authenticated user does not have a wallet.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        WalletApiExamples
                            .CURRENT_WALLET_NOT_FOUND
                )
            )
        )
    })
    @GetMapping("/me")
    public ResponseEntity<GetCurrentWalletResponse>
    getCurrentWallet(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        Jwt jwt
    ) {
        UUID ownerId = UUID.fromString(
            jwt.getSubject()
        );

        GetCurrentWalletResult result =
            getCurrentWalletUseCase
                .getCurrentWallet(ownerId);

        return ResponseEntity.ok(
            GetCurrentWalletResponse.from(result)
        );
    }
}
