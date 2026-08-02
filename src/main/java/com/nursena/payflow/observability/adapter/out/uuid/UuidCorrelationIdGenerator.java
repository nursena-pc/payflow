package com.nursena.payflow.observability.adapter.out.uuid;

import java.util.UUID;

import com.nursena.payflow.observability.domain.CorrelationIdGenerator;

public final class UuidCorrelationIdGenerator
    implements CorrelationIdGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID()
            .toString();
    }
}