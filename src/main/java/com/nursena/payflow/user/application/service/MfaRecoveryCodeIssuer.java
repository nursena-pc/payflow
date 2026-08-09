package com.nursena.payflow.user.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.GeneratedMfaRecoveryCode;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeDigestPort;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeGenerationPort;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeRepositoryPort;
import com.nursena.payflow.user.domain.model.MfaRecoveryCode;
import com.nursena.payflow.user.domain.model.MfaRecoveryCodeDigest;
import org.springframework.stereotype.Component;

@Component
public class MfaRecoveryCodeIssuer {

    static final int CODE_COUNT = 10;
    private static final int MAX_GENERATION_ATTEMPTS = CODE_COUNT * 4;

    private final MfaRecoveryCodeGenerationPort generationPort;
    private final MfaRecoveryCodeDigestPort digestPort;
    private final MfaRecoveryCodeRepositoryPort repository;

    public MfaRecoveryCodeIssuer(
        MfaRecoveryCodeGenerationPort generationPort,
        MfaRecoveryCodeDigestPort digestPort,
        MfaRecoveryCodeRepositoryPort repository
    ) {
        this.generationPort = generationPort;
        this.digestPort = digestPort;
        this.repository = repository;
    }

    public List<String> issue(UUID userId, Instant now) {
        UUID checkedUserId = Objects.requireNonNull(
            userId,
            "userId must not be null"
        );
        Instant issuedAt = Objects.requireNonNull(
            now,
            "now must not be null"
        );

        List<String> plaintextCodes = new ArrayList<>(CODE_COUNT);
        List<MfaRecoveryCode> recoveryCodes =
            new ArrayList<>(CODE_COUNT);
        Set<MfaRecoveryCodeDigest> digests = new HashSet<>();

        int attempts = 0;

        while (
            recoveryCodes.size() < CODE_COUNT
                && attempts < MAX_GENERATION_ATTEMPTS
        ) {
            attempts++;
            GeneratedMfaRecoveryCode generated = generationPort.generate();
            MfaRecoveryCodeDigest digest = digestPort.digest(
                generated.value()
            );

            if (!digests.add(digest)) {
                continue;
            }

            plaintextCodes.add(generated.value());
            recoveryCodes.add(MfaRecoveryCode.issue(
                UUID.randomUUID(),
                checkedUserId,
                digest,
                issuedAt
            ));
        }

        if (recoveryCodes.size() != CODE_COUNT) {
            throw new IllegalStateException(
                "could not generate a unique MFA recovery-code set"
            );
        }

        List<MfaRecoveryCode> saved = repository.saveAll(recoveryCodes);

        if (saved.size() != CODE_COUNT) {
            throw new IllegalStateException(
                "recovery-code persistence returned an incomplete set"
            );
        }

        return List.copyOf(plaintextCodes);
    }
}
