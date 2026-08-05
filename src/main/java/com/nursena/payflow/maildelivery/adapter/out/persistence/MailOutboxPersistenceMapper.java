package com.nursena.payflow.maildelivery.adapter.out.persistence;

import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;

final class MailOutboxPersistenceMapper {

    private MailOutboxPersistenceMapper() {
    }

    static MailOutboxMessage toDomain(MailOutboxMessageJpaEntity entity) {
        return MailOutboxMessage.rehydrate(
            entity.getId(),
            entity.getUserId(),
            entity.getPurpose(),
            entity.getRecipient(),
            entity.getSubject(),
            entity.getProtectedContent(),
            entity.getMessageId(),
            entity.getStatus(),
            entity.getAttemptCount(),
            entity.getAvailableAt(),
            entity.getExpiresAt(),
            entity.getLockedAt(),
            entity.getLockedUntil(),
            entity.getLockedBy(),
            entity.getCreatedAt(),
            entity.getSentAt(),
            entity.getLastError()
        );
    }
}
