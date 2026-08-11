package com.nursena.payflow.user.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.StepUpGrant;
import com.nursena.payflow.user.domain.model.StepUpGrantDigest;
import com.nursena.payflow.user.domain.model.StepUpPurpose;

public interface StepUpGrantRepositoryPort {

    StepUpGrant save(StepUpGrant grant);

    Optional<StepUpGrant> findByDigestForUpdate(StepUpGrantDigest digest);

    int supersedeUnconsumedBySubjectAndPurpose(
        UUID subjectId,
        StepUpPurpose purpose,
        Instant supersededAt
    );
}
