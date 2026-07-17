package com.nursena.payflow.transaction.adapter.in.web;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.UUID;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiConfiguration;
import com.nursena.payflow.configuration.TransactionApiExamples;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyCommand;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyResult;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Transfers",
    description = "Authenticated wallet transfer operations."
)
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferMoneyController {

    private static final String IDEMPOTENCY_KEY_HEADER =
        "Idempotency-Key";

    private final TransferMoneyUseCase transferMoneyUseCase;

    public TransferMoneyController(
        TransferMoneyUseCase transferMoneyUseCase
    ) {
        this.transferMoneyUseCase = transferMoneyUseCase;
    }

    @Operation(
        operationId = "transferMoney",
        summary = "Transfer money",
        description =
            "Transfers a simulated amount from the "
                + "authenticated user's wallet to another wallet. "
                + "Repeated requests with the same idempotency "
                + "key and payload return the existing result."
    )
    @SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Transfer completed.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        TransferMoneyResponse.class
                ),
                examples = @ExampleObject(
                    value =
                        TransactionApiExamples
                            .TRANSFER_SUCCESS
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description =
                "The request is invalid or the "
                    + "Idempotency-Key header is missing.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = {
                    @ExampleObject(
                        name = "missingIdempotencyKey",
                        summary =
                            "Idempotency-Key header is missing",
                        value =
                            TransactionApiExamples
                                .MISSING_IDEMPOTENCY_KEY
                    ),
                    @ExampleObject(
                        name = "validationFailed",
                        summary =
                            "Request body validation failed",
                        value =
                            TransactionApiExamples
                                .TRANSFER_VALIDATION_ERROR
                    )
                }
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
                "The source or target wallet "
                    + "could not be found.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        TransactionApiExamples
                            .TRANSFER_WALLET_NOT_FOUND
                )
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description =
                "The request conflicts with an existing "
                    + "idempotency record or concurrent update.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = {
                    @ExampleObject(
                        name = "idempotencyKeyConflict",
                        summary =
                            "Key reused with a different payload",
                        value =
                            TransactionApiExamples
                                .IDEMPOTENCY_KEY_CONFLICT
                    ),
                    @ExampleObject(
                        name = "requestInProgress",
                        summary =
                            "Original request is still running",
                        value =
                            TransactionApiExamples
                                .IDEMPOTENCY_REQUEST_IN_PROGRESS
                    ),
                    @ExampleObject(
                        name = "concurrentWalletUpdate",
                        summary =
                            "A wallet changed concurrently",
                        value =
                            TransactionApiExamples
                                .TRANSFER_CONCURRENT_UPDATE
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description =
                "A transfer business rule was violated.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = {
                    @ExampleObject(
                        name = "invalidIdempotencyKey",
                        summary =
                            "Idempotency key length is invalid",
                        value =
                            TransactionApiExamples
                                .INVALID_IDEMPOTENCY_KEY
                    ),
                    @ExampleObject(
                        name = "insufficientBalance",
                        summary =
                            "Source wallet balance is insufficient",
                        value =
                            TransactionApiExamples
                                .INSUFFICIENT_BALANCE
                    ),
                    @ExampleObject(
                        name = "walletNotActive",
                        summary =
                            "Source or target wallet is inactive",
                        value =
                            TransactionApiExamples
                                .TRANSFER_WALLET_NOT_ACTIVE
                    ),
                    @ExampleObject(
                        name = "currencyMismatch",
                        summary =
                            "Wallet currencies do not match",
                        value =
                            TransactionApiExamples
                                .TRANSFER_CURRENCY_MISMATCH
                    )
                }
            )
        )
    })
    @PostMapping
    public ResponseEntity<TransferMoneyResponse> transfer(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        Jwt jwt,

        @Parameter(
            name = IDEMPOTENCY_KEY_HEADER,
            in = ParameterIn.HEADER,
            description =
                "Unique request key used to make "
                    + "the transfer idempotent.",
            required = true,
            example = "transfer-20260717-001",
            schema = @Schema(
                type = "string",
                minLength = 1,
                maxLength = 100
            )
        )
        @RequestHeader(IDEMPOTENCY_KEY_HEADER)
        String idempotencyKey,

        @Valid
        @RequestBody
        TransferMoneyRequest request
    ) {
        UUID ownerId = UUID.fromString(
            jwt.getSubject()
        );

        TransferMoneyResult result =
            transferMoneyUseCase.transfer(
                new TransferMoneyCommand(
                    ownerId,
                    request.targetWalletId(),
                    request.amount(),
                    idempotencyKey
                )
            );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                TransferMoneyResponse.from(result)
            );
    }
}
