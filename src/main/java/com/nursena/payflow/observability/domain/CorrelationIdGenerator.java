package com.nursena.payflow.observability.domain;

@FunctionalInterface
public interface CorrelationIdGenerator {

    String generate();
}