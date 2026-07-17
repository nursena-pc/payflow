package com.nursena.payflow.wallet.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiConfiguration;
import com.nursena.payflow.configuration.WalletApiExamples;
import com.nursena.payflow.wallet.application.port.in.TopUpWalletCommand;
import com.nursena.payflow.wallet.application.port.in.TopUpWalletResult;
import com.nursena.payflow.wallet.application.port.in.TopUpWalletUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(
    name = "Wallets",
    description = "Authenticated wallet management operations."
)

@RestController
@RequestMapping("/api/v1/wallets/me/top-ups")
public class TopUpWalletController {

    private final TopUpWalletUseCase topUpWalletUseCase;

    public TopUpWalletController(
        TopUpWalletUseCase topUpWalletUseCase
    ) {
        this.topUpWalletUseCase = topUpWalletUseCase;
    }

    @Operation(
        operationId = "topUpCurrentWallet",
        summary = "Top up current wallet",
        description =
            "Credits a simulated amount to the authenticated "
                + "user's wallet."
    )
    @SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Wallet topped up.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        TopUpWalletResponse.class
                ),
                examples = @ExampleObject(
                    value =
                        WalletApiExamples.TOP_UP_WALLET
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
                        WalletApiExamples
                            .TOP_UP_VALIDATION_ERROR
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
                            .TOP_UP_WALLET_NOT_FOUND
                )
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description =
                "The wallet was updated concurrently.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        WalletApiExamples
                            .WALLET_CONCURRENT_UPDATE
                )
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description =
                "The wallet is not active.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        WalletApiExamples.WALLET_NOT_ACTIVE
                )
            )
        )
    })

    @PostMapping
    public ResponseEntity<TopUpWalletResponse> topUpWallet(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        Jwt jwt,

        @Valid
        @RequestBody
        TopUpWalletRequest request
    ) {
        UUID ownerId = UUID.fromString(
            jwt.getSubject()
        );

        TopUpWalletResult result =
            topUpWalletUseCase.topUp(
                new TopUpWalletCommand(
                    ownerId,
                    request.amount()
                )
            );

        return ResponseEntity.ok(
            TopUpWalletResponse.from(result)
        );
    }
}
