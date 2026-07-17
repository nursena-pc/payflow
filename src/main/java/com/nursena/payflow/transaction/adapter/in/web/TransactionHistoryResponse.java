package com.nursena.payflow.transaction.adapter.in.web;

import java.util.List;
import java.util.Objects;

import com.nursena.payflow.transaction.application.model.TransactionHistoryPage;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "TransactionHistoryResponse",
    description =
        "Paginated transaction history of the "
            + "authenticated user's wallet."
)
public record TransactionHistoryResponse(

    @Schema(
        description =
            "Transactions contained in the current page."
    )
    List<TransactionHistoryItemResponse> items,

    @Schema(
        description = "Zero-based current page index.",
        example = "0",
        minimum = "0"
    )
    int page,

    @Schema(
        description = "Requested page size.",
        example = "20",
        minimum = "1",
        maximum = "100"
    )
    int size,

    @Schema(
        description =
            "Total number of matching transactions.",
        example = "1",
        minimum = "0"
    )
    long totalElements,

    @Schema(
        description = "Total number of result pages.",
        example = "1",
        minimum = "0"
    )
    int totalPages,

    @Schema(
        description =
            "Whether this is the first result page.",
        example = "true"
    )
    boolean first,

    @Schema(
        description =
            "Whether this is the last result page.",
        example = "true"
    )
    boolean last,

    @Schema(
        description =
            "Whether another result page is available.",
        example = "false"
    )
    boolean hasNext,

    @Schema(
        description =
            "Whether a previous result page is available.",
        example = "false"
    )
    boolean hasPrevious
) {

    static TransactionHistoryResponse from(
        TransactionHistoryPage result
    ) {
        Objects.requireNonNull(
            result,
            "result must not be null"
        );

        List<TransactionHistoryItemResponse> items =
            result.items()
                .stream()
                .map(
                    TransactionHistoryItemResponse::from
                )
                .toList();

        return new TransactionHistoryResponse(
            items,
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.first(),
            result.last(),
            result.hasNext(),
            result.hasPrevious()
        );
    }
}
