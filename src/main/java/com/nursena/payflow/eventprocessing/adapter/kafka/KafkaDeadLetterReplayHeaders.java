package com.nursena.payflow.eventprocessing.adapter.kafka;

public final class KafkaDeadLetterReplayHeaders {

    public static final String REPLAY_ORIGIN_ID =
        "payflow-replay-origin-id";

    public static final String REPLAY_ATTEMPT =
        "payflow-replay-attempt";

    private KafkaDeadLetterReplayHeaders() {
    }
}
