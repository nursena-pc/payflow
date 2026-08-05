package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.assertj.core.api.Assertions
    .assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import org.junit.jupiter.api.Test;

class AccountActionCredentialTest {

    private static final Instant ISSUED_AT =
        Instant.parse("2026-08-05T12:00:00Z");

    private static final Instant EXPIRES_AT =
        ISSUED_AT.plus(Duration.ofHours(24));

    @Test
    void shouldIssueActiveCredential() {
        AccountActionCredential credential =
            activeCredential();

        assertThat(credential.isActiveAt(ISSUED_AT))
            .isTrue();
        assertThat(
            credential.isActiveAt(
                EXPIRES_AT.minusNanos(1)
            )
        )
            .isTrue();
        assertThat(credential.isActiveAt(EXPIRES_AT))
            .isFalse();
        assertThat(credential.isResolved())
            .isFalse();
    }

    @Test
    void shouldConsumeCredentialExactlyOnce() {
        Instant consumedAt =
            ISSUED_AT.plusSeconds(60);

        AccountActionCredential consumed =
            activeCredential().consume(consumedAt);

        assertThat(consumed.consumedAt())
            .isEqualTo(consumedAt);
        assertThat(consumed.supersededAt())
            .isNull();
        assertThat(consumed.isResolved())
            .isTrue();
        assertThat(consumed.isActiveAt(consumedAt))
            .isFalse();

        assertThatThrownBy(() ->
            consumed.consume(
                consumedAt.plusSeconds(1)
            )
        )
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            )
            .hasMessage(
                "Account action credential is invalid."
            );
    }

    @Test
    void shouldRejectExpiredCredentialGenerically() {
        assertThatThrownBy(() ->
            activeCredential().consume(EXPIRES_AT)
        )
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            )
            .hasMessage(
                "Account action credential is invalid."
            );
    }

    @Test
    void shouldSupersedeUnresolvedCredential() {
        Instant supersededAt =
            ISSUED_AT.plusSeconds(30);

        AccountActionCredential superseded =
            activeCredential().supersede(
                supersededAt
            );

        assertThat(superseded.supersededAt())
            .isEqualTo(supersededAt);
        assertThat(superseded.consumedAt())
            .isNull();
        assertThat(superseded.isResolved())
            .isTrue();
        assertThat(
            superseded.supersede(
                supersededAt.plusSeconds(1)
            )
        )
            .isSameAs(superseded);
    }

    @Test
    void shouldRejectConflictingTerminalState() {
        assertThatThrownBy(() ->
            AccountActionCredential.rehydrate(
                AccountActionCredentialId.of(
                    UUID.randomUUID()
                ),
                UUID.randomUUID(),
                AccountActionCredentialPurpose
                    .EMAIL_VERIFICATION,
                digest(),
                ISSUED_AT,
                EXPIRES_AT,
                ISSUED_AT.plusSeconds(10),
                ISSUED_AT.plusSeconds(20)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "consumedAt and supersededAt "
                    + "must not appear together"
            );
    }

    @Test
    void shouldRejectConsumptionOutsideLifetime() {
        assertThatThrownBy(() ->
            activeCredential().consume(
                ISSUED_AT.minusNanos(1)
            )
        )
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            );
    }

    private static AccountActionCredential
    activeCredential() {
        return AccountActionCredential.issue(
            AccountActionCredentialId.of(
                UUID.fromString(
                    "d17fcbdb-050d-4507-a85f-c784710a6f40"
                )
            ),
            UUID.fromString(
                "b723b34d-3ee1-4d38-b88d-9a9607c7fc12"
            ),
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION,
            digest(),
            ISSUED_AT,
            EXPIRES_AT
        );
    }

    private static AccountActionCredentialDigest digest() {
        return AccountActionCredentialDigest.of(
            new byte[
                AccountActionCredentialDigest
                    .SHA_256_LENGTH_BYTES
            ]
        );
    }
}
