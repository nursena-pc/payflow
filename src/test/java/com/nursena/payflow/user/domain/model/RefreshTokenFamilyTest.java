package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RefreshTokenFamilyTest {

    private static final RefreshTokenFamilyId
        FAMILY_ID =
        RefreshTokenFamilyId.of(
            UUID.fromString(
                "70000000-0000-0000-0000-000000000001"
            )
        );

    private static final UUID USER_ID =
        UUID.fromString(
            "70000000-0000-0000-0000-000000000002"
        );

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-26T13:00:00Z"
        );

    private static final Instant EXPIRES_AT =
        CREATED_AT.plusSeconds(86_400);

    @Test
    void shouldCreateActiveFamily() {
        RefreshTokenFamily family =
            activeFamily();

        assertThat(family.id())
            .isEqualTo(FAMILY_ID);

        assertThat(family.userId())
            .isEqualTo(USER_ID);

        assertThat(family.createdAt())
            .isEqualTo(CREATED_AT);

        assertThat(family.expiresAt())
            .isEqualTo(EXPIRES_AT);

        assertThat(family.isRevoked())
            .isFalse();

        assertThat(
            family.isActiveAt(
                CREATED_AT
            )
        )
            .isTrue();
    }

    @Test
    void shouldNotBeActiveBeforeCreation() {
        RefreshTokenFamily family =
            activeFamily();

        assertThat(
            family.isActiveAt(
                CREATED_AT.minusNanos(1)
            )
        )
            .isFalse();
    }

    @Test
    void shouldTreatExpirationBoundaryAsInactive() {
        RefreshTokenFamily family =
            activeFamily();

        assertThat(
            family.isActiveAt(
                EXPIRES_AT.minusNanos(1)
            )
        )
            .isTrue();

        assertThat(
            family.isActiveAt(
                EXPIRES_AT
            )
        )
            .isFalse();

        assertThat(
            family.isExpiredAt(
                EXPIRES_AT
            )
        )
            .isTrue();
    }

    @Test
    void shouldRevokeWithoutMutatingOriginal() {
        RefreshTokenFamily original =
            activeFamily();

        Instant revokedAt =
            CREATED_AT.plusSeconds(60);

        RefreshTokenFamily revoked =
            original.revoke(
                RefreshTokenFamilyRevocationReason
                    .CURRENT_SESSION_LOGOUT,
                revokedAt
            );

        assertThat(original.isRevoked())
            .isFalse();

        assertThat(revoked.isRevoked())
            .isTrue();

        assertThat(revoked.revokedAt())
            .isEqualTo(revokedAt);

        assertThat(revoked.revocationReason())
            .isEqualTo(
                RefreshTokenFamilyRevocationReason
                    .CURRENT_SESSION_LOGOUT
            );

        assertThat(
            revoked.isActiveAt(
                revokedAt
            )
        )
            .isFalse();
    }

    @Test
    void shouldKeepFirstRevocationIdempotently() {
        RefreshTokenFamily revoked =
            activeFamily().revoke(
                RefreshTokenFamilyRevocationReason
                    .REUSE_DETECTED,
                CREATED_AT.plusSeconds(30)
            );

        RefreshTokenFamily repeated =
            revoked.revoke(
                RefreshTokenFamilyRevocationReason
                    .ALL_SESSIONS_LOGOUT,
                CREATED_AT.plusSeconds(60)
            );

        assertThat(repeated)
            .isSameAs(revoked);

        assertThat(repeated.revocationReason())
            .isEqualTo(
                RefreshTokenFamilyRevocationReason
                    .REUSE_DETECTED
            );
    }

    @Test
    void shouldRejectInvalidLifetime() {
        assertThatThrownBy(() ->
            RefreshTokenFamily.create(
                FAMILY_ID,
                USER_ID,
                CREATED_AT,
                CREATED_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "expiresAt must be after createdAt"
            );
    }

    @Test
    void shouldRejectIncompleteRevocationState() {
        assertThatThrownBy(() ->
            RefreshTokenFamily.rehydrate(
                FAMILY_ID,
                USER_ID,
                CREATED_AT,
                EXPIRES_AT,
                CREATED_AT.plusSeconds(10),
                null
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "revokedAt and revocationReason "
                    + "must appear together"
            );

        assertThatThrownBy(() ->
            RefreshTokenFamily.rehydrate(
                FAMILY_ID,
                USER_ID,
                CREATED_AT,
                EXPIRES_AT,
                null,
                RefreshTokenFamilyRevocationReason
                    .REUSE_DETECTED
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "revokedAt and revocationReason "
                    + "must appear together"
            );
    }

    @Test
    void shouldRejectRevocationBeforeCreation() {
        assertThatThrownBy(() ->
            activeFamily().revoke(
                RefreshTokenFamilyRevocationReason
                    .ADMINISTRATIVE_REVOCATION,
                CREATED_AT.minusSeconds(1)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "revokedAt must not be before createdAt"
            );
    }

    private static RefreshTokenFamily activeFamily() {
        return RefreshTokenFamily.create(
            FAMILY_ID,
            USER_ID,
            CREATED_AT,
            EXPIRES_AT
        );
    }
}
