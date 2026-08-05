package com.nursena.payflow.maildelivery.adapter.in.scheduling;

import java.util.Objects;

import com.nursena.payflow.maildelivery.application.port.in.DispatchMailOutboxCommand;
import com.nursena.payflow.maildelivery.application.port.in.DispatchMailOutboxResult;
import com.nursena.payflow.maildelivery.application.port.in.DispatchMailOutboxUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class MailOutboxScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        MailOutboxScheduler.class
    );

    private final DispatchMailOutboxUseCase useCase;
    private final DispatchMailOutboxCommand command;

    MailOutboxScheduler(
        DispatchMailOutboxUseCase useCase,
        MailOutboxPollingProperties properties
    ) {
        this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        this.command = new DispatchMailOutboxCommand(
            properties.workerId(),
            properties.batchSize(),
            properties.leaseDuration()
        );
    }

    @Scheduled(
        fixedDelayString = "${payflow.mail.outbox.polling.fixed-delay}",
        initialDelayString = "${payflow.mail.outbox.polling.initial-delay}"
    )
    public void dispatchAvailableMail() {
        try {
            DispatchMailOutboxResult result = useCase.dispatch(command);
            if (result.claimedCount() == 0) {
                LOGGER.debug("No available mail outbox messages were found.");
                return;
            }
            LOGGER.info(
                "Mail outbox cycle completed. claimed={}, sent={}, retried={}, failed={}, unresolved={}",
                result.claimedCount(),
                result.sentCount(),
                result.retriedCount(),
                result.failedCount(),
                result.unresolvedCount()
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Mail outbox cycle failed. workerId={}",
                command.workerId(),
                exception
            );
        }
    }
}
