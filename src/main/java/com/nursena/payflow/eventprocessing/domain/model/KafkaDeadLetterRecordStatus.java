package com.nursena.payflow.eventprocessing.domain.model;

public enum KafkaDeadLetterRecordStatus {

    RECEIVED,
    REPLAYING,
    REPLAYED,
    REPLAY_FAILED,
    DISCARDED
}
