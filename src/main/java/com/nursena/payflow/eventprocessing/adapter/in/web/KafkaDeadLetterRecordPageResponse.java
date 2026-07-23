package com.nursena.payflow.eventprocessing.adapter.in.web;

import java.util.List;
import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordPage;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "KafkaDeadLetterRecordPageResponse",
    description =
        "Paginated Kafka dead-letter administration records."
)
public record KafkaDeadLetterRecordPageResponse(

    @Schema(
        description =
            "Dead-letter records contained in "
                + "the current page."
    )
    List<KafkaDeadLetterRecordSummaryResponse> items,

    @Schema(
        description = "Zero-based page index.",
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
            "Total number of matching records.",
        example = "42",
        minimum = "0"
    )
    long totalElements,

    @Schema(
        description = "Total number of result pages.",
        example = "3",
        minimum = "0"
    )
    int totalPages,

    @Schema(
        description = "Whether this is the first page.",
        example = "true"
    )
    boolean first,

    @Schema(
        description = "Whether this is the last page.",
        example = "false"
    )
    boolean last,

    @Schema(
        description = "Whether another page exists.",
        example = "true"
    )
    boolean hasNext,

    @Schema(
        description = "Whether a previous page exists.",
        example = "false"
    )
    boolean hasPrevious
) {

    static KafkaDeadLetterRecordPageResponse from(
        KafkaDeadLetterRecordPage page
    ) {
        KafkaDeadLetterRecordPage validatedPage =
            Objects.requireNonNull(
                page,
                "page must not be null"
            );

        List<KafkaDeadLetterRecordSummaryResponse>
            items =
            validatedPage.items()
                .stream()
                .map(
                    KafkaDeadLetterRecordSummaryResponse
                        ::from
                )
                .toList();

        return new KafkaDeadLetterRecordPageResponse(
            items,
            validatedPage.page(),
            validatedPage.size(),
            validatedPage.totalElements(),
            validatedPage.totalPages(),
            validatedPage.first(),
            validatedPage.last(),
            validatedPage.hasNext(),
            validatedPage.hasPrevious()
        );
    }
}
