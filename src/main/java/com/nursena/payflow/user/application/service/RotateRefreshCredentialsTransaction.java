package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsResult;
import com.nursena.payflow.user.application.port.out.AccessTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.GeneratedAccessToken;
import com.nursena.payflow.user.application.port.out.GeneratedRefreshToken;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.RefreshTokenRecordId;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RotateRefreshCredentialsTransaction {

    private final RefreshTokenRecordRepositoryPort
        recordRepository;

    private final RefreshTokenFamilyRepositoryPort
        familyRepository;

    private final UserRepositoryPort userRepository;

    private final RefreshTokenGenerationPort
        refreshTokenGeneration;

    private final RefreshTokenDigestPort
        refreshTokenDigest;

    private final AccessTokenGenerationPort
        accessTokenGeneration;

    private final RefreshSessionLifetimePolicy
        lifetimePolicy;

    private final Clock clock;

    RotateRefreshCredentialsTransaction(
        RefreshTokenRecordRepositoryPort
            recordRepository,
        RefreshTokenFamilyRepositoryPort
            familyRepository,
        UserRepositoryPort userRepository,
        RefreshTokenGenerationPort
            refreshTokenGeneration,
        RefreshTokenDigestPort refreshTokenDigest,
        AccessTokenGenerationPort
            accessTokenGeneration,
        RefreshSessionLifetimePolicy lifetimePolicy,
        Clock clock
    ) {
        this.recordRepository =
            Objects.requireNonNull(
                recordRepository,
                "recordRepository must not be null"
            );

        this.familyRepository =
            Objects.requireNonNull(
                familyRepository,
                "familyRepository must not be null"
            );

        this.userRepository =
            Objects.requireNonNull(
                userRepository,
                "userRepository must not be null"
            );

        this.refreshTokenGeneration =
            Objects.requireNonNull(
                refreshTokenGeneration,
                "refreshTokenGeneration must not be null"
            );

        this.refreshTokenDigest =
            Objects.requireNonNull(
                refreshTokenDigest,
                "refreshTokenDigest must not be null"
            );

        this.accessTokenGeneration =
            Objects.requireNonNull(
                accessTokenGeneration,
                "accessTokenGeneration must not be null"
            );

        this.lifetimePolicy =
            Objects.requireNonNull(
                lifetimePolicy,
                "lifetimePolicy must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Transactional
    public RotateRefreshCredentialsOutcome rotate(
        RefreshTokenDigest currentDigest
    ) {
        RefreshTokenDigest checkedDigest =
            Objects.requireNonNull(
                currentDigest,
                "currentDigest must not be null"
            );

        RefreshTokenRecord currentRecord =
            recordRepository
                .findByDigestForUpdate(
                    checkedDigest
                )
                .orElse(null);

        if (currentRecord == null) {
            return rejected();
        }

        RefreshTokenFamily family =
            familyRepository
                .findByIdForUpdate(
                    currentRecord.familyId()
                )
                .orElse(null);

        if (family == null) {
            return rejected();
        }

        Instant rotatedAt =
            clock.instant()
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        if (currentRecord.isConsumed()) {
            revokeActiveFamilyForReuse(
                family,
                rotatedAt
            );

            return rejected();
        }

        if (
            !currentRecord.isActiveAt(
                family,
                rotatedAt
            )
        ) {
            return rejected();
        }

        User user =
            userRepository
                .findById(
                    family.userId()
                )
                .filter(candidate ->
                    candidate.status()
                        == UserStatus.ACTIVE
                )
                .orElse(null);

        if (user == null) {
            return rejected();
        }

        GeneratedRefreshToken generatedToken =
            refreshTokenGeneration.generate();

        RefreshTokenDigest successorDigest =
            refreshTokenDigest.digest(
                generatedToken.value()
            );

        RefreshTokenRecordId successorId =
            RefreshTokenRecordId.of(
                UUID.randomUUID()
            );

        Instant successorExpiresAt =
            lifetimePolicy
                .refreshTokenExpiresAt(
                    rotatedAt,
                    family.expiresAt()
                );

        RefreshTokenRecord successor =
            RefreshTokenRecord.issue(
                successorId,
                family,
                successorDigest,
                rotatedAt,
                successorExpiresAt
            );

        RefreshTokenRecord consumedCurrent =
            currentRecord.consume(
                successorId,
                rotatedAt,
                family
            );

        RefreshTokenRecord savedSuccessor =
            recordRepository.save(
                successor
            );

        recordRepository.save(
            consumedCurrent
        );

        GeneratedAccessToken accessToken =
            accessTokenGeneration.generate(
                user
            );

        RotateRefreshCredentialsResult result =
            new RotateRefreshCredentialsResult(
                accessToken.value(),
                accessToken.expiresAt(),
                generatedToken.value(),
                savedSuccessor.expiresAt()
            );

        return new RotateRefreshCredentialsOutcome
            .Succeeded(result);
    }

    private void revokeActiveFamilyForReuse(
        RefreshTokenFamily family,
        Instant detectedAt
    ) {
        if (!family.isActiveAt(detectedAt)) {
            return;
        }

        RefreshTokenFamily revokedFamily =
            family.revoke(
                RefreshTokenFamilyRevocationReason
                    .REUSE_DETECTED,
                detectedAt
            );

        familyRepository.save(
            revokedFamily
        );
    }

    private static RotateRefreshCredentialsOutcome
    rejected() {
        return RotateRefreshCredentialsOutcome
            .Rejected
            .INSTANCE;
    }
}
