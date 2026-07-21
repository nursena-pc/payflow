package com.nursena.payflow.eventprocessing.application.port.out;

import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;

public interface TransferCompletedEventHandlerPort {

    void handle(
        TransferCompletedEvent event
    );
}
