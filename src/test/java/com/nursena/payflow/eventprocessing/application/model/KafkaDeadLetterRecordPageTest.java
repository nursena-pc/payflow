package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.junit.jupiter.api.Test;

class KafkaDeadLetterRecordPageTest {

    @Test
    void shouldCreateImmutableCopyOfItems() {
        KafkaDeadLetterRecordSummary item =
            createSummary();

        List<KafkaDeadLetterRecordSummary>
            sourceItems =
            new ArrayList<>();

        sourceItems.add(item);

        KafkaDeadLetterRecordPage page =
            new KafkaDeadLetterRecordPage(
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
        KafkaDeadLetterRecordPage page =
            new KafkaDeadLetterRecordPage(
                List.of(createSummary()),
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
        KafkaDeadLetterRecordPage page =
            new KafkaDeadLetterRecordPage(
                List.of(createSummary()),
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
        KafkaDeadLetterRecordPage page =
            new KafkaDeadLetterRecordPage(
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
                new KafkaDeadLetterRecordPage(
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
                new KafkaDeadLetterRecordPage(
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

    private static KafkaDeadLetterRecordSummary
    createSummary() {
        UUID recordId =
            UUID.fromString(
                "637398d5-0a02-4d10-a9af-c783ef92778b"
            );

        return new KafkaDeadLetterRecordSummary(
            recordId,
            KafkaDeadLetterRecordStatus.RECEIVED,
            "wallet.transfer.completed.dlt",
            0,
            42L,
            "wallet.transfer.completed",
            0,
            41L,
            "payflow-transfer-consumer",
            "java.lang.IllegalStateException",
            0,
            0,
            Instant.parse(
                "2026-07-22T12:00:00Z"
            ),
            null,
            recordId,
            true
        );
    }
}
