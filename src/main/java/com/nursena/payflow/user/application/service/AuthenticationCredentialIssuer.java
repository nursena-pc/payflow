package com.nursena.payflow.user.application.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.AuthenticatedUserResult;
import com.nursena.payflow.user.application.port.out.AccessTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.GeneratedAccessToken;
import com.nursena.payflow.user.application.port.out.GeneratedRefreshToken;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyId;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.RefreshTokenRecordId;
import com.nursena.payflow.user.domain.model.User;
import org.springframework.stereotype.Component;

@Component
class AuthenticationCredentialIssuer {

    private final RefreshTokenGenerationPort refreshTokenGeneration;
    private final RefreshTokenDigestPort refreshTokenDigest;
    private final RefreshTokenFamilyRepositoryPort familyRepository;
    private final RefreshTokenRecordRepositoryPort recordRepository;
    private final AccessTokenGenerationPort accessTokenGeneration;
    private final RefreshSessionLifetimePolicy lifetimePolicy;

    AuthenticationCredentialIssuer(
        RefreshTokenGenerationPort refreshTokenGeneration,
        RefreshTokenDigestPort refreshTokenDigest,
        RefreshTokenFamilyRepositoryPort familyRepository,
        RefreshTokenRecordRepositoryPort recordRepository,
        AccessTokenGenerationPort accessTokenGeneration,
        RefreshSessionLifetimePolicy lifetimePolicy
    ) {
        this.refreshTokenGeneration = refreshTokenGeneration;
        this.refreshTokenDigest = refreshTokenDigest;
        this.familyRepository = familyRepository;
        this.recordRepository = recordRepository;
        this.accessTokenGeneration = accessTokenGeneration;
        this.lifetimePolicy = lifetimePolicy;
    }

    AuthenticatedUserResult issue(User user, Instant issuedAt) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");

        GeneratedRefreshToken generatedRefreshToken =
            refreshTokenGeneration.generate();
        RefreshTokenDigest digest = refreshTokenDigest.digest(
            generatedRefreshToken.value()
        );
        RefreshTokenFamily savedFamily = familyRepository.save(
            RefreshTokenFamily.create(
                RefreshTokenFamilyId.of(UUID.randomUUID()),
                user.id(),
                issuedAt,
                lifetimePolicy.familyExpiresAt(issuedAt)
            )
        );
        RefreshTokenRecord savedRecord = recordRepository.save(
            RefreshTokenRecord.issue(
                RefreshTokenRecordId.of(UUID.randomUUID()),
                savedFamily,
                digest,
                issuedAt,
                lifetimePolicy.refreshTokenExpiresAt(
                    issuedAt,
                    savedFamily.expiresAt()
                )
            )
        );
        GeneratedAccessToken accessToken = accessTokenGeneration.generate(user);

        return new AuthenticatedUserResult(
            accessToken.value(),
            accessToken.expiresAt(),
            generatedRefreshToken.value(),
            savedRecord.expiresAt()
        );
    }
}
