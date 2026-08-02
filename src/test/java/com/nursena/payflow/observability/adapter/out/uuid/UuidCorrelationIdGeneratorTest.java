package com.nursena.payflow.observability.adapter.out.uuid;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidCorrelationIdGeneratorTest {

    private final UuidCorrelationIdGenerator generator =
        new UuidCorrelationIdGenerator();

    @Test
    void shouldGenerateCanonicalUuid() {
        String generated =
            generator.generate();

        assertThat(
            UUID.fromString(generated)
                .toString()
        )
            .isEqualTo(generated);
    }

    @Test
    void shouldGenerateDistinctValues() {
        assertThat(
            generator.generate()
        )
            .isNotEqualTo(
                generator.generate()
            );
    }
}