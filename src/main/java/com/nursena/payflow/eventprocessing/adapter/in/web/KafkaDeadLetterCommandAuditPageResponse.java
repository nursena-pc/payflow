package com.nursena.payflow.eventprocessing.adapter.in.web;

import java.util.List;
import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditPage;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "KafkaDeadLetterCommandAuditPageResponse",
    description =
        "Paginated Kafka dead-letter command audit entries."
)
public record KafkaDeadLetterCommandAuditPageResponse(
    List<KafkaDeadLetterCommandAuditEntryResponse> items,

    @Schema(minimum = "0", example = "0")
    int page,

    @Schema(minimum = "1", maximum = "100", example = "20")
    int size,

    @Schema(minimum = "0", example = "42")
    long totalElements,

    @Schema(minimum = "0", example = "3")
    int totalPages,

    boolean first,
    boolean last,
    boolean hasNext,
    boolean hasPrevious
) {
    static KafkaDeadLetterCommandAuditPageResponse from(
        KafkaDeadLetterCommandAuditPage page
    ) {
        KafkaDeadLetterCommandAuditPage validatedPage =
            Objects.requireNonNull(
                page,
                "page must not be null"
            );

        List<KafkaDeadLetterCommandAuditEntryResponse> items =
            validatedPage.items()
                .stream()
                .map(
                    KafkaDeadLetterCommandAuditEntryResponse
                        ::from
                )
                .toList();

        return new KafkaDeadLetterCommandAuditPageResponse(
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
