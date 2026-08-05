package com.nursena.payflow.maildelivery.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;

public interface MailOutboxClaimPort {

    List<MailOutboxMessage> claimAvailable(
        String workerId,
        Instant claimedAt,
        Duration leaseDuration,
        int batchSize
    );
}
