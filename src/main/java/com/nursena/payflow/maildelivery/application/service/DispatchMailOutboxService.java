package com.nursena.payflow.maildelivery.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import com.nursena.payflow.maildelivery.application.model.MailRetryDecision;
import com.nursena.payflow.maildelivery.application.policy.MailRetryPolicy;
import com.nursena.payflow.maildelivery.application.port.in.DispatchMailOutboxCommand;
import com.nursena.payflow.maildelivery.application.port.in.DispatchMailOutboxResult;
import com.nursena.payflow.maildelivery.application.port.in.DispatchMailOutboxUseCase;
import com.nursena.payflow.maildelivery.application.port.out.MailDeliveryPort;
import com.nursena.payflow.maildelivery.application.port.out.MailOutboxClaimPort;
import com.nursena.payflow.maildelivery.application.port.out.MailOutboxLifecyclePort;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DispatchMailOutboxService
    implements DispatchMailOutboxUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        DispatchMailOutboxService.class
    );

    private final MailOutboxClaimPort claimPort;
    private final MailDeliveryPort deliveryPort;
    private final MailOutboxLifecyclePort lifecyclePort;
    private final MailRetryPolicy retryPolicy;
    private final Clock clock;

    public DispatchMailOutboxService(
        MailOutboxClaimPort claimPort,
        MailDeliveryPort deliveryPort,
        MailOutboxLifecyclePort lifecyclePort,
        MailRetryPolicy retryPolicy,
        Clock clock
    ) {
        this.claimPort = Objects.requireNonNull(claimPort, "claimPort must not be null");
        this.deliveryPort = Objects.requireNonNull(deliveryPort, "deliveryPort must not be null");
        this.lifecyclePort = Objects.requireNonNull(lifecyclePort, "lifecyclePort must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public DispatchMailOutboxResult dispatch(
        DispatchMailOutboxCommand command
    ) {
        DispatchMailOutboxCommand checkedCommand = Objects.requireNonNull(
            command,
            "command must not be null"
        );
        Instant claimedAt = now();
        List<MailOutboxMessage> claimed = claimPort.claimAvailable(
            checkedCommand.workerId(),
            claimedAt,
            checkedCommand.leaseDuration(),
            checkedCommand.batchSize()
        );

        int sent = 0;
        int retried = 0;
        int failed = 0;
        int unresolved = 0;

        for (MailOutboxMessage message : claimed) {
            Outcome outcome = dispatchOne(message, checkedCommand.workerId());
            switch (outcome) {
                case SENT -> sent++;
                case RETRIED -> retried++;
                case FAILED -> failed++;
                case UNRESOLVED -> unresolved++;
            }
        }

        return new DispatchMailOutboxResult(
            claimed.size(),
            sent,
            retried,
            failed,
            unresolved
        );
    }

    private Outcome dispatchOne(
        MailOutboxMessage message,
        String workerId
    ) {
        Instant beforeSend = now();
        if (!message.expiresAt().isAfter(beforeSend)) {
            return persistTerminalFailure(
                message,
                workerId,
                beforeSend,
                "DeliveryWindowExpired"
            );
        }

        try {
            deliveryPort.send(message);
            lifecyclePort.markSent(
                message.id(),
                workerId,
                now()
            );
            return Outcome.SENT;
        } catch (RuntimeException deliveryFailure) {
            return handleFailure(
                message,
                workerId,
                deliveryFailure
            );
        }
    }

    private Outcome handleFailure(
        MailOutboxMessage message,
        String workerId,
        RuntimeException deliveryFailure
    ) {
        Instant failedAt = now();
        String error = sanitizedFailureType(deliveryFailure);
        try {
            MailRetryDecision decision = retryPolicy.decide(message, failedAt);
            if (decision.shouldRetry()) {
                lifecyclePort.scheduleRetry(
                    message.id(),
                    workerId,
                    failedAt,
                    decision.nextAvailableAt(),
                    error
                );
                return Outcome.RETRIED;
            }
            lifecyclePort.markFailed(
                message.id(),
                workerId,
                failedAt,
                error
            );
            return Outcome.FAILED;
        } catch (RuntimeException persistenceFailure) {
            LOGGER.error(
                "Mail outbox outcome could not be persisted. messageId={}, purpose={}, workerId={}",
                message.id(),
                message.purpose(),
                workerId,
                persistenceFailure
            );
            return Outcome.UNRESOLVED;
        }
    }

    private Outcome persistTerminalFailure(
        MailOutboxMessage message,
        String workerId,
        Instant failedAt,
        String error
    ) {
        try {
            lifecyclePort.markFailed(
                message.id(),
                workerId,
                failedAt,
                error
            );
            return Outcome.FAILED;
        } catch (RuntimeException persistenceFailure) {
            LOGGER.error(
                "Expired mail outcome could not be persisted. messageId={}, purpose={}, workerId={}",
                message.id(),
                message.purpose(),
                workerId,
                persistenceFailure
            );
            return Outcome.UNRESOLVED;
        }
    }

    private static String sanitizedFailureType(RuntimeException exception) {
        String simpleName = exception.getClass().getSimpleName();
        return simpleName.isBlank()
            ? "MailDeliveryFailure"
            : simpleName;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private enum Outcome {
        SENT,
        RETRIED,
        FAILED,
        UNRESOLVED
    }
}
