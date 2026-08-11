package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.StepUpAuthorizationPolicy;
import com.nursena.payflow.user.application.port.out.StepUpGrantDigestPort;
import com.nursena.payflow.user.application.port.out.StepUpGrantRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidStepUpGrantException;
import com.nursena.payflow.user.domain.model.StepUpGrant;
import com.nursena.payflow.user.domain.model.StepUpGrantDigest;
import com.nursena.payflow.user.domain.model.StepUpPurpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistentStepUpAuthorizationPolicy
    implements StepUpAuthorizationPolicy {

    private final StepUpGrantDigestPort digestPort;
    private final StepUpGrantRepositoryPort repository;
    private final Clock clock;

    public PersistentStepUpAuthorizationPolicy(
        StepUpGrantDigestPort digestPort,
        StepUpGrantRepositoryPort repository,
        Clock clock
    ) {
        this.digestPort = digestPort;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void requireAndConsume(
        UUID subjectId,
        StepUpPurpose purpose,
        String grantToken
    ) {
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        StepUpGrantDigest digest = digestSafely(grantToken);
        StepUpGrant grant = repository.findByDigestForUpdate(digest)
            .orElseThrow(InvalidStepUpGrantException::new);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        if (!grant.subjectId().equals(subjectId)
            || grant.purpose() != purpose
            || !grant.isUsableAt(now)) {
            throw new InvalidStepUpGrantException();
        }

        repository.save(grant.consume(now));
    }

    private StepUpGrantDigest digestSafely(String grantToken) {
        if (grantToken == null || grantToken.isBlank()
            || grantToken.length() > 256) {
            throw new InvalidStepUpGrantException();
        }
        try {
            return digestPort.digest(grantToken);
        }
        catch (IllegalArgumentException exception) {
            throw new InvalidStepUpGrantException();
        }
    }
}
