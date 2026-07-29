package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RevokeCurrentRefreshSessionTransaction {

    private final RefreshTokenRecordRepositoryPort
        recordRepository;

    private final RefreshTokenFamilyRepositoryPort
        familyRepository;

    private final Clock clock;

    RevokeCurrentRefreshSessionTransaction(
        RefreshTokenRecordRepositoryPort
            recordRepository,
        RefreshTokenFamilyRepositoryPort
            familyRepository,
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

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Transactional
    public void revoke(
        RefreshTokenDigest digest
    ) {
        RefreshTokenDigest checkedDigest =
            Objects.requireNonNull(
                digest,
                "digest must not be null"
            );

        RefreshTokenRecord record =
            recordRepository
                .findByDigestForUpdate(
                    checkedDigest
                )
                .orElse(null);

        if (record == null) {
            return;
        }

        RefreshTokenFamily family =
            familyRepository
                .findByIdForUpdate(
                    record.familyId()
                )
                .orElse(null);

        if (family == null) {
            return;
        }

        Instant revokedAt =
            clock.instant()
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        if (!family.isActiveAt(revokedAt)) {
            return;
        }

        RefreshTokenFamily revokedFamily =
            family.revoke(
                RefreshTokenFamilyRevocationReason
                    .CURRENT_SESSION_LOGOUT,
                revokedAt
            );

        familyRepository.save(
            revokedFamily
        );
    }
}
