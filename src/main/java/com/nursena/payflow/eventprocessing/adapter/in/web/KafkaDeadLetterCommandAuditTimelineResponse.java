package com.nursena.payflow.eventprocessing.adapter.in.web;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditTimeline;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "KafkaDeadLetterCommandAuditTimelineResponse",
    description =
        "Chronological audit timeline for one Kafka "
            + "dead-letter operator command."
)
public record KafkaDeadLetterCommandAuditTimelineResponse(
    @Schema(format = "uuid")
    UUID commandId,

    @Schema(
        description =
            "Whether the timeline contains both "
                + "ATTEMPTED and COMPLETED entries."
    )
    boolean complete,

    List<KafkaDeadLetterCommandAuditEntryResponse> entries
) {
    static KafkaDeadLetterCommandAuditTimelineResponse from(
        KafkaDeadLetterCommandAuditTimeline timeline
    ) {
        KafkaDeadLetterCommandAuditTimeline validatedTimeline =
            Objects.requireNonNull(
                timeline,
                "timeline must not be null"
            );

        return new KafkaDeadLetterCommandAuditTimelineResponse(
            validatedTimeline.commandId(),
            validatedTimeline.complete(),
            validatedTimeline.entries()
                .stream()
                .map(
                    KafkaDeadLetterCommandAuditEntryResponse
                        ::from
                )
                .toList()
        );
    }
}
