package com.nursena.payflow.wallet.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiConfiguration;
import com.nursena.payflow.configuration.WalletApiExamples;
import com.nursena.payflow.wallet.application.port.in.OpenWalletCommand;
import com.nursena.payflow.wallet.application.port.in.OpenWalletResult;
import com.nursena.payflow.wallet.application.port.in.OpenWalletUseCase;
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
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/wallets")
public class OpenWalletController {

    private final OpenWalletUseCase openWalletUseCase;

    public OpenWalletController(
        OpenWalletUseCase openWalletUseCase
    ) {
        this.openWalletUseCase = openWalletUseCase;
    }

    @Operation(
        operationId = "openWallet",
        summary = "Open a wallet",
        description =
            "Creates one wallet for the authenticated user."
    )
    @SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Wallet created.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = OpenWalletResponse.class
                ),
                examples = @ExampleObject(
                    value = WalletApiExamples.OPEN_WALLET
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
                            .OPEN_WALLET_VALIDATION_ERROR
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description =
                "Bearer token is missing or invalid."
        ),
        @ApiResponse(
            responseCode = "409",
            description =
                "The authenticated user already has a wallet.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        WalletApiExamples
                            .WALLET_ALREADY_EXISTS
                )
            )
        )
    })
    @PostMapping
    public ResponseEntity<OpenWalletResponse> openWallet(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        Jwt jwt,

        @Valid
        @RequestBody
        OpenWalletRequest request
    ) {
        UUID ownerId = UUID.fromString(
            jwt.getSubject()
        );

        OpenWalletResult result =
            openWalletUseCase.open(
                new OpenWalletCommand(
                    ownerId,
                    request.currency()
                )
            );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(OpenWalletResponse.from(result));
    }
}
