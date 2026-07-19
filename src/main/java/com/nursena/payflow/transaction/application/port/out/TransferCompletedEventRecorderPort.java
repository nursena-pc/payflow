package com.nursena.payflow.transaction.application.port.out;

import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;

public interface TransferCompletedEventRecorderPort {

    void record(
        TransferCompletedEvent event
    );
}
