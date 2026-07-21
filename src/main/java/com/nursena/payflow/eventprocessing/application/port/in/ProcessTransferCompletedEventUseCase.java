package com.nursena.payflow.eventprocessing.application.port.in;

import com.nursena.payflow.eventprocessing.application.model.ProcessTransferCompletedEventCommand;
import com.nursena.payflow.eventprocessing.application.model.ProcessTransferCompletedEventResult;

public interface ProcessTransferCompletedEventUseCase {

    ProcessTransferCompletedEventResult process(
        ProcessTransferCompletedEventCommand command
    );
}
