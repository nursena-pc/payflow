package com.nursena.payflow.outbox.application.port.out;

import com.nursena.payflow.outbox.application.model.OutboxBacklogSnapshot;

public interface OutboxBacklogQueryPort {

    OutboxBacklogSnapshot loadSnapshot();
}
