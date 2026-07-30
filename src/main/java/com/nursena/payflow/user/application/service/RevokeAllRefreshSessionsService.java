package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.nursena.payflow.user.application.port.in.RevokeAllRefreshSessionsCommand;
import com.nursena.payflow.user.application.port.in.RevokeAllRefreshSessionsUseCase;
import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeAllRefreshSessionsService
    implements RevokeAllRefreshSessionsUseCase {

    private final RefreshTokenFamilyRepositoryPort
        familyRepository;

    private final Clock clock;

    public RevokeAllRefreshSessionsService(
        RefreshTokenFamilyRepositoryPort
            familyRepository,
        Clock clock
    ) {
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

    @Override
    @Transactional
    public void revoke(
        RevokeAllRefreshSessionsCommand command
    ) {
        RevokeAllRefreshSessionsCommand
            checkedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        Instant revokedAt =
            clock.instant()
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        familyRepository
            .revokeAllActiveByUserId(
                checkedCommand.userId(),
                revokedAt,
                RefreshTokenFamilyRevocationReason
                    .ALL_SESSIONS_LOGOUT
            );
    }
}
