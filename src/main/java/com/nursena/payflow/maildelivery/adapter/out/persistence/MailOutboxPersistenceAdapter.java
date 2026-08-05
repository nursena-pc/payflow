package com.nursena.payflow.maildelivery.adapter.out.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.maildelivery.application.port.out.MailOutboxClaimPort;
import com.nursena.payflow.maildelivery.application.port.out.MailOutboxEnqueuePort;
import com.nursena.payflow.maildelivery.application.port.out.MailOutboxLifecyclePort;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class MailOutboxPersistenceAdapter
    implements MailOutboxEnqueuePort,
    MailOutboxClaimPort,
    MailOutboxLifecyclePort {

    private static final String SUPERSEDED_ERROR = "SupersededByNewerCredential";
    private static final String EXPIRED_ERROR = "DeliveryWindowExpired";

    private final SpringDataMailOutboxMessageRepository repository;

    MailOutboxPersistenceAdapter(
        SpringDataMailOutboxMessageRepository repository
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    @Transactional
    public void replaceUnresolved(
        MailOutboxMessage message,
        Instant replacedAt
    ) {
        MailOutboxMessage checkedMessage = Objects.requireNonNull(message, "message must not be null");
        Instant checkedReplacedAt = Objects.requireNonNull(replacedAt, "replacedAt must not be null");

        List<MailOutboxMessageJpaEntity> unresolved = repository.findUnresolvedForUpdate(
            checkedMessage.userId(),
            checkedMessage.purpose().name()
        );
        for (MailOutboxMessageJpaEntity entity : unresolved) {
            MailOutboxMessage superseded = MailOutboxPersistenceMapper
                .toDomain(entity)
                .failWithoutClaim(checkedReplacedAt, SUPERSEDED_ERROR);
            entity.applyState(superseded);
        }
        repository.save(new MailOutboxMessageJpaEntity(checkedMessage));
        repository.flush();
    }

    @Override
    @Transactional
    public List<MailOutboxMessage> claimAvailable(
        String workerId,
        Instant claimedAt,
        Duration leaseDuration,
        int batchSize
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        Objects.requireNonNull(claimedAt, "claimedAt must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");

        List<MailOutboxMessageJpaEntity> expired = repository.findExpiredForUpdate(
            claimedAt,
            batchSize
        );
        for (MailOutboxMessageJpaEntity entity : expired) {
            entity.applyState(
                MailOutboxPersistenceMapper
                    .toDomain(entity)
                    .failWithoutClaim(claimedAt, EXPIRED_ERROR)
            );
        }

        List<MailOutboxMessageJpaEntity> entities = repository.findClaimableForUpdate(
            claimedAt,
            batchSize
        );
        List<MailOutboxMessage> claimed = new ArrayList<>(entities.size());
        for (MailOutboxMessageJpaEntity entity : entities) {
            MailOutboxMessage claimedMessage = MailOutboxPersistenceMapper
                .toDomain(entity)
                .claim(workerId, claimedAt, leaseDuration);
            entity.applyState(claimedMessage);
            claimed.add(claimedMessage);
        }
        repository.flush();
        return List.copyOf(claimed);
    }

    @Override
    @Transactional
    public void markSent(
        UUID messageId,
        String workerId,
        Instant deliveredAt
    ) {
        updateLocked(
            messageId,
            current -> current.markSent(workerId, deliveredAt)
        );
    }

    @Override
    @Transactional
    public void scheduleRetry(
        UUID messageId,
        String workerId,
        Instant failedAt,
        Instant nextAvailableAt,
        String error
    ) {
        updateLocked(
            messageId,
            current -> current.scheduleRetry(
                workerId,
                failedAt,
                nextAvailableAt,
                error
            )
        );
    }

    @Override
    @Transactional
    public void markFailed(
        UUID messageId,
        String workerId,
        Instant failedAt,
        String error
    ) {
        updateLocked(
            messageId,
            current -> current.markFailed(
                workerId,
                failedAt,
                error
            )
        );
    }

    private void updateLocked(
        UUID messageId,
        java.util.function.UnaryOperator<MailOutboxMessage> transition
    ) {
        MailOutboxMessageJpaEntity entity = repository
            .findByIdForUpdate(Objects.requireNonNull(messageId, "messageId must not be null"))
            .orElseThrow(() -> new IllegalStateException("mail outbox message was not found"));
        MailOutboxMessage updated = transition.apply(
            MailOutboxPersistenceMapper.toDomain(entity)
        );
        entity.applyState(updated);
        repository.flush();
    }
}
