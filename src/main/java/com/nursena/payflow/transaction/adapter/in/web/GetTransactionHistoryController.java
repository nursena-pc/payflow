package com.nursena.payflow.transaction.adapter.in.web;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiConfiguration;
import com.nursena.payflow.configuration.TransactionApiExamples;
import com.nursena.payflow.transaction.application.model.TransactionDirection;
import com.nursena.payflow.transaction.application.model.TransactionHistoryFilter;
import com.nursena.payflow.transaction.application.model.TransactionHistoryPage;
import com.nursena.payflow.transaction.application.port.in.GetTransactionHistoryQuery;
import com.nursena.payflow.transaction.application.port.in.GetTransactionHistoryUseCase;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Transactions",
    description =
        "Authenticated transaction history operations."
)
@RestController
@RequestMapping("/api/v1/transactions")
public class GetTransactionHistoryController {

    private final GetTransactionHistoryUseCase
        getTransactionHistoryUseCase;

    public GetTransactionHistoryController(
        GetTransactionHistoryUseCase
            getTransactionHistoryUseCase
    ) {
        this.getTransactionHistoryUseCase =
            getTransactionHistoryUseCase;
    }

    @Operation(
        operationId = "getTransactionHistory",
        summary = "Get transaction history",
        description =
            "Returns the authenticated user's wallet "
                + "transactions ordered by creation time "
                + "descending and transaction identifier "
                + "descending."
    )
    @SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description =
                "Transaction history returned.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        TransactionHistoryResponse.class
                ),
                examples = @ExampleObject(
                    value =
                        TransactionApiExamples
                            .TRANSACTION_HISTORY_SUCCESS
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description =
                "A pagination or filter value is invalid.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                ),
                examples = @ExampleObject(
                    value =
                        TransactionApiExamples
                            .TRANSACTION_HISTORY_VALIDATION_ERROR
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
                        TransactionApiExamples
                            .TRANSACTION_HISTORY_WALLET_NOT_FOUND
                )
            )
        )
    })
    @GetMapping("/me")
    public ResponseEntity<TransactionHistoryResponse>
    getTransactionHistory(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        Jwt jwt,

        @Parameter(
            name = "page",
            description = "Zero-based result page index.",
            example = "0",
            schema = @Schema(
                type = "integer",
                minimum = "0",
                defaultValue = "0"
            )
        )
        @RequestParam(
            name = "page",
            defaultValue = "0"
        )
        @Min(
            value = 0,
            message = "page must not be negative"
        )
        int page,

        @Parameter(
            name = "size",
            description =
                "Number of transactions returned per page.",
            example = "20",
            schema = @Schema(
                type = "integer",
                minimum = "1",
                maximum = "100",
                defaultValue = "20"
            )
        )
        @RequestParam(
            name = "size",
            defaultValue = "20"
        )
        @Min(
            value = 1,
            message = "size must be greater than zero"
        )
        @Max(
            value = GetTransactionHistoryQuery.MAX_SIZE,
            message = "size must not exceed 100"
        )
        int size,

        @Parameter(
            name = "direction",
            description =
                "Transaction direction relative to "
                    + "the current wallet.",
            example = "OUTGOING",
            schema = @Schema(
                allowableValues = {
                    "INCOMING",
                    "OUTGOING"
                }
            )
        )
        @RequestParam(
            name = "direction",
            required = false
        )
        TransactionDirection direction,

        @Parameter(
            name = "status",
            description = "Transaction status filter.",
            example = "COMPLETED",
            schema = @Schema(
                allowableValues = {
                    "PENDING",
                    "COMPLETED",
                    "FAILED"
                }
            )
        )
        @RequestParam(
            name = "status",
            required = false
        )
        TransactionStatus status,

        @Parameter(
            name = "from",
            description =
                "Inclusive lower bound for the "
                    + "transaction creation time.",
            example = "2026-07-01T00:00:00Z",
            schema = @Schema(
                type = "string",
                format = "date-time"
            )
        )
        @RequestParam(
            name = "from",
            required = false
        )
        @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        )
        Instant from,

        @Parameter(
            name = "to",
            description =
                "Exclusive upper bound for the "
                    + "transaction creation time. It may equal "
                    + "the from value.",
            example = "2026-08-01T00:00:00Z",
            schema = @Schema(
                type = "string",
                format = "date-time"
            )
        )
        @RequestParam(
            name = "to",
            required = false
        )
        @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE_TIME
        )
        Instant to
    ) {
        UUID ownerId = UUID.fromString(
            jwt.getSubject()
        );

        TransactionHistoryFilter filter =
            new TransactionHistoryFilter(
                direction,
                status,
                from,
                to
            );

        TransactionHistoryPage result =
            getTransactionHistoryUseCase
                .getTransactionHistory(
                    new GetTransactionHistoryQuery(
                        ownerId,
                        page,
                        size,
                        filter
                    )
                );

        return ResponseEntity.ok(
            TransactionHistoryResponse.from(result)
        );
    }
}
