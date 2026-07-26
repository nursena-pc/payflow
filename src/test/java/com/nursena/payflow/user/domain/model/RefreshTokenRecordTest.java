package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RefreshTokenRecordTest {

    private static final RefreshTokenFamilyId
        FAMILY_ID =
        RefreshTokenFamilyId.of(
            UUID.fromString(
                "71000000-0000-0000-0000-000000000001"
            )
        );

    private static final RefreshTokenFamilyId
        OTHER_FAMILY_ID =
        RefreshTokenFamilyId.of(
            UUID.fromString(
                "71000000-0000-0000-0000-000000000002"
            )
        );

    private static final RefreshTokenRecordId
        TOKEN_ID =
        RefreshTokenRecordId.of(
            UUID.fromString(
                "71000000-0000-0000-0000-000000000003"
            )
        );

    private static final RefreshTokenRecordId
        SUCCESSOR_ID =
        RefreshTokenRecordId.of(
            UUID.fromString(
                "71000000-0000-0000-0000-000000000004"
            )
        );

    private static final UUID USER_ID =
        UUID.fromString(
            "71000000-0000-0000-0000-000000000005"
        );

    private static final Instant FAMILY_CREATED_AT =
        Instant.parse(
            "2026-07-26T13:00:00Z"
        );

    private static final Instant FAMILY_EXPIRES_AT =
        FAMILY_CREATED_AT.plusSeconds(86_400);

    private static final Instant TOKEN_EXPIRES_AT =
        FAMILY_CREATED_AT.plusSeconds(3_600);

    @Test
    void shouldIssueActiveToken() {
        RefreshTokenRecord token =
            activeToken();

        assertThat(token.id())
            .isEqualTo(TOKEN_ID);

        assertThat(token.familyId())
            .isEqualTo(FAMILY_ID);

        assertThat(token.isConsumed())
            .isFalse();

        assertThat(
            token.isActiveAt(
                activeFamily(),
                FAMILY_CREATED_AT
            )
        )
            .isTrue();
    }

    @Test
    void shouldNotBeActiveBeforeIssuance() {
        RefreshTokenRecord token =
            activeToken();

        assertThat(
            token.isActiveAt(
                activeFamily(),
                FAMILY_CREATED_AT.minusNanos(1)
            )
        )
            .isFalse();
    }

    @Test
    void shouldRejectIssuanceBeforeFamilyCreation() {
        assertThatThrownBy(() ->
            RefreshTokenRecord.issue(
                TOKEN_ID,
                activeFamily(),
                digest((byte) 1),
                FAMILY_CREATED_AT.minusSeconds(1),
                TOKEN_EXPIRES_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "issuedAt must not be before "
                    + "family createdAt"
            );
    }

    @Test
    void shouldRejectExpirationAfterFamilyExpiration() {
        assertThatThrownBy(() ->
            RefreshTokenRecord.issue(
                TOKEN_ID,
                activeFamily(),
                digest((byte) 1),
                FAMILY_CREATED_AT,
                FAMILY_EXPIRES_AT.plusSeconds(1)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "token expiresAt must not be after "
                    + "family expiresAt"
            );
    }

    @Test
    void shouldRejectIssuanceForRevokedFamily() {
        RefreshTokenFamily revoked =
            activeFamily().revoke(
                RefreshTokenFamilyRevocationReason
                    .REUSE_DETECTED,
                FAMILY_CREATED_AT.plusSeconds(30)
            );

        assertThatThrownBy(() ->
            RefreshTokenRecord.issue(
                TOKEN_ID,
                revoked,
                digest((byte) 1),
                FAMILY_CREATED_AT.plusSeconds(60),
                TOKEN_EXPIRES_AT
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "family must be active when "
                    + "a token is issued"
            );
    }

    @Test
    void shouldConsumeWithoutMutatingOriginal() {
        RefreshTokenRecord original =
            activeToken();

        Instant consumedAt =
            FAMILY_CREATED_AT.plusSeconds(600);

        RefreshTokenRecord consumed =
            original.consume(
                SUCCESSOR_ID,
                consumedAt,
                activeFamily()
            );

        assertThat(original.isConsumed())
            .isFalse();

        assertThat(consumed.isConsumed())
            .isTrue();

        assertThat(consumed.consumedAt())
            .isEqualTo(consumedAt);

        assertThat(consumed.successorId())
            .isEqualTo(SUCCESSOR_ID);

        assertThat(
            consumed.isActiveAt(
                activeFamily(),
                consumedAt
            )
        )
            .isFalse();
    }

    @Test
    void shouldRejectSelfSuccessor() {
        assertThatThrownBy(() ->
            activeToken().consume(
                TOKEN_ID,
                FAMILY_CREATED_AT.plusSeconds(10),
                activeFamily()
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "a token must not replace itself"
            );
    }

    @Test
    void shouldRejectSecondConsumption() {
        RefreshTokenRecord consumed =
            activeToken().consume(
                SUCCESSOR_ID,
                FAMILY_CREATED_AT.plusSeconds(10),
                activeFamily()
            );

        assertThatThrownBy(() ->
            consumed.consume(
                RefreshTokenRecordId.of(
                    UUID.randomUUID()
                ),
                FAMILY_CREATED_AT.plusSeconds(20),
                activeFamily()
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "refresh token is already consumed"
            );
    }

    @Test
    void shouldRejectExpiredTokenConsumption() {
        assertThatThrownBy(() ->
            activeToken().consume(
                SUCCESSOR_ID,
                TOKEN_EXPIRES_AT,
                activeFamily()
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "refresh token must not be expired"
            );
    }

    @Test
    void shouldRejectConsumptionForRevokedFamily() {
        Instant revokedAt =
            FAMILY_CREATED_AT.plusSeconds(300);

        RefreshTokenFamily revoked =
            activeFamily().revoke(
                RefreshTokenFamilyRevocationReason
                    .CURRENT_SESSION_LOGOUT,
                revokedAt
            );

        assertThatThrownBy(() ->
            activeToken().consume(
                SUCCESSOR_ID,
                revokedAt.plusSeconds(1),
                revoked
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "family must be active when "
                    + "a token is consumed"
            );
    }

    @Test
    void shouldRejectConsumptionForDifferentFamily() {
        RefreshTokenFamily otherFamily =
            RefreshTokenFamily.create(
                OTHER_FAMILY_ID,
                USER_ID,
                FAMILY_CREATED_AT,
                FAMILY_EXPIRES_AT
            );

        assertThatThrownBy(() ->
            activeToken().consume(
                SUCCESSOR_ID,
                FAMILY_CREATED_AT.plusSeconds(10),
                otherFamily
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "token familyId must match family id"
            );
    }

    @Test
    void shouldRejectIncompleteConsumedState() {
        assertThatThrownBy(() ->
            RefreshTokenRecord.rehydrate(
                TOKEN_ID,
                FAMILY_ID,
                digest((byte) 1),
                FAMILY_CREATED_AT,
                TOKEN_EXPIRES_AT,
                FAMILY_CREATED_AT.plusSeconds(10),
                null,
                activeFamily()
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "consumedAt and successorId "
                    + "must appear together"
            );

        assertThatThrownBy(() ->
            RefreshTokenRecord.rehydrate(
                TOKEN_ID,
                FAMILY_ID,
                digest((byte) 1),
                FAMILY_CREATED_AT,
                TOKEN_EXPIRES_AT,
                null,
                SUCCESSOR_ID,
                activeFamily()
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "consumedAt and successorId "
                    + "must appear together"
            );
    }

    @Test
    void shouldRehydrateConsumedTokenInRevokedFamily() {
        Instant consumedAt =
            FAMILY_CREATED_AT.plusSeconds(300);

        Instant revokedAt =
            consumedAt.plusSeconds(300);

        RefreshTokenFamily revoked =
            RefreshTokenFamily.rehydrate(
                FAMILY_ID,
                USER_ID,
                FAMILY_CREATED_AT,
                FAMILY_EXPIRES_AT,
                revokedAt,
                RefreshTokenFamilyRevocationReason
                    .REUSE_DETECTED
            );

        RefreshTokenRecord token =
            RefreshTokenRecord.rehydrate(
                TOKEN_ID,
                FAMILY_ID,
                digest((byte) 1),
                FAMILY_CREATED_AT,
                TOKEN_EXPIRES_AT,
                consumedAt,
                SUCCESSOR_ID,
                revoked
            );

        assertThat(token.isConsumed())
            .isTrue();

        assertThat(token.successorId())
            .isEqualTo(SUCCESSOR_ID);

        assertThat(
            token.isActiveAt(
                revoked,
                consumedAt
            )
        )
            .isFalse();
    }

    private static RefreshTokenFamily activeFamily() {
        return RefreshTokenFamily.create(
            FAMILY_ID,
            USER_ID,
            FAMILY_CREATED_AT,
            FAMILY_EXPIRES_AT
        );
    }

    private static RefreshTokenRecord activeToken() {
        return RefreshTokenRecord.issue(
            TOKEN_ID,
            activeFamily(),
            digest((byte) 1),
            FAMILY_CREATED_AT,
            TOKEN_EXPIRES_AT
        );
    }

    private static RefreshTokenDigest digest(
        byte value
    ) {
        byte[] bytes =
            new byte[
                RefreshTokenDigest
                    .SHA_256_LENGTH_BYTES
            ];

        Arrays.fill(bytes, value);

        return RefreshTokenDigest.of(bytes);
    }
}
