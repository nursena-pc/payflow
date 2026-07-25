package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class KafkaDeadLetterCommandAuditPageTest {

    @Test
    void shouldCreateImmutableCopyOfItems() {
        KafkaDeadLetterCommandAudit item =
            createAttemptedAudit();

        List<KafkaDeadLetterCommandAudit>
            sourceItems =
            new ArrayList<>();

        sourceItems.add(item);

        KafkaDeadLetterCommandAuditPage page =
            new KafkaDeadLetterCommandAuditPage(
                sourceItems,
                0,
                20,
                1,
                1
            );

        sourceItems.clear();

        assertThat(page.items())
            .containsExactly(item);
        assertThatThrownBy(
            () -> page.items().clear()
        )
            .isInstanceOf(
                UnsupportedOperationException.class
            );
    }

    @Test
    void shouldDescribeFirstPage() {
        KafkaDeadLetterCommandAuditPage page =
            new KafkaDeadLetterCommandAuditPage(
                List.of(createAttemptedAudit()),
                0,
                20,
                41,
                3
            );

        assertThat(page.first()).isTrue();
        assertThat(page.last()).isFalse();
        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrevious()).isFalse();
    }

    @Test
    void shouldDescribeLastPage() {
        KafkaDeadLetterCommandAuditPage page =
            new KafkaDeadLetterCommandAuditPage(
                List.of(createAttemptedAudit()),
                2,
                20,
                41,
                3
            );

        assertThat(page.first()).isFalse();
        assertThat(page.last()).isTrue();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isTrue();
    }

    @Test
    void shouldTreatEmptyResultAsFirstAndLastPage() {
        KafkaDeadLetterCommandAuditPage page =
            new KafkaDeadLetterCommandAuditPage(
                List.of(),
                0,
                20,
                0,
                0
            );

        assertThat(page.first()).isTrue();
        assertThat(page.last()).isTrue();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isFalse();
    }

    @Test
    void shouldRejectNegativeTotalElements() {
        assertThatThrownBy(
            () ->
                new KafkaDeadLetterCommandAuditPage(
                    List.of(),
                    0,
                    20,
                    -1,
                    0
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "totalElements must not be negative"
            );
    }

    @Test
    void shouldRejectNegativeTotalPages() {
        assertThatThrownBy(
            () ->
                new KafkaDeadLetterCommandAuditPage(
                    List.of(),
                    0,
                    20,
                    0,
                    -1
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "totalPages must not be negative"
            );
    }

    private static KafkaDeadLetterCommandAudit
    createAttemptedAudit() {
        return KafkaDeadLetterCommandAudit
            .attempted(
                UUID.fromString(
                    "45a97a7c-291b-4392-8b14-2d9d3df813a2"
                ),
                UUID.fromString(
                    "b2fdb860-df65-4c43-ab69-87f930dd16dc"
                ),
                UUID.fromString(
                    "152468c4-eeba-4a17-b19c-dd0fd4ca63a7"
                ),
                UUID.fromString(
                    "9f9085f8-a4bf-412d-bc3b-9c0de54ca383"
                ),
                KafkaDeadLetterCommandType.REPLAY,
                Instant.parse(
                    "2026-07-25T10:00:00Z"
                )
            );
    }
}
