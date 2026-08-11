package com.nursena.payflow.user.application.service;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.IssueStepUpGrantResult;
import com.nursena.payflow.user.application.port.out.GeneratedStepUpGrant;
import com.nursena.payflow.user.application.port.out.StepUpGrantDigestPort;
import com.nursena.payflow.user.application.port.out.StepUpGrantGenerationPort;
import com.nursena.payflow.user.application.port.out.StepUpGrantRepositoryPort;
import com.nursena.payflow.user.domain.model.StepUpGrant;
import com.nursena.payflow.user.domain.model.StepUpPurpose;
import org.springframework.stereotype.Component;

@Component
class StepUpGrantIssuer {

    private final StepUpGrantGenerationPort generation;
    private final StepUpGrantDigestPort digest;
    private final StepUpGrantRepositoryPort repository;
    private final StepUpGrantLifetimePolicy lifetimePolicy;

    StepUpGrantIssuer(
        StepUpGrantGenerationPort generation,
        StepUpGrantDigestPort digest,
        StepUpGrantRepositoryPort repository,
        StepUpGrantLifetimePolicy lifetimePolicy
    ) {
        this.generation = generation;
        this.digest = digest;
        this.repository = repository;
        this.lifetimePolicy = lifetimePolicy;
    }

    IssueStepUpGrantResult issue(
        UUID subjectId,
        StepUpPurpose purpose,
        Instant issuedAt
    ) {
        repository.supersedeUnconsumedBySubjectAndPurpose(
            subjectId,
            purpose,
            issuedAt
        );

        GeneratedStepUpGrant generated = generation.generate();
        Instant expiresAt = lifetimePolicy.expiresAt(issuedAt);
        StepUpGrant saved = repository.save(
            StepUpGrant.issue(
                UUID.randomUUID(),
                subjectId,
                purpose,
                digest.digest(generated.value()),
                issuedAt,
                expiresAt
            )
        );

        return new IssueStepUpGrantResult(
            generated.value(),
            saved.purpose().value(),
            saved.expiresAt()
        );
    }
}
