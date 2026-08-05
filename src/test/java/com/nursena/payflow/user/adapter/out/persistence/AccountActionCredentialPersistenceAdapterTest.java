package com.nursena.payflow.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.domain.model
    .AccountActionCredential;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialDigest;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialId;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountActionCredentialPersistenceAdapterTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "0d147a7c-7232-4145-902f-b4655cc903ba"
        );

    private static final Instant ISSUED_AT =
        Instant.parse("2026-08-05T12:00:00Z");

    @Mock
    private SpringDataAccountActionCredentialRepository
        repository;

    private AccountActionCredentialPersistenceAdapter
        adapter;

    @BeforeEach
    void setUp() {
        adapter =
            new AccountActionCredentialPersistenceAdapter(
                repository
            );
    }

    @Test
    void shouldSaveAndRestoreCredential() {
        AccountActionCredential credential =
            activeCredential();

        when(repository.saveAndFlush(
            any(AccountActionCredentialJpaEntity.class)
        ))
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        AccountActionCredential saved =
            adapter.save(credential);

        assertThat(saved.id()).isEqualTo(credential.id());
        assertThat(saved.userId()).isEqualTo(USER_ID);
        assertThat(saved.purpose())
            .isEqualTo(credential.purpose());
        assertThat(saved.digest())
            .isEqualTo(credential.digest());
        assertThat(saved.issuedAt())
            .isEqualTo(credential.issuedAt());
        assertThat(saved.expiresAt())
            .isEqualTo(credential.expiresAt());
    }

    @Test
    void shouldDelegateSupersession() {
        when(repository.supersedeUnresolved(
            USER_ID,
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION,
            ISSUED_AT
        ))
            .thenReturn(1);

        int updated = adapter.supersedeUnresolved(
            USER_ID,
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION,
            ISSUED_AT
        );

        assertThat(updated).isEqualTo(1);
        verify(repository).supersedeUnresolved(
            USER_ID,
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION,
            ISSUED_AT
        );
    }

    @Test
    void shouldFindAndLockCredentialByDigestAndPurpose() {
        AccountActionCredential credential =
            activeCredential();
        byte[] persistedDigest =
            credential.digest().value();
        AccountActionCredentialJpaEntity entity =
            new AccountActionCredentialJpaEntity(
                credential.id().value(),
                credential.userId(),
                credential.purpose(),
                persistedDigest,
                credential.issuedAt(),
                credential.expiresAt(),
                null,
                null
            );

        when(
            repository.findByDigestAndPurposeForUpdate(
                credential.digest().value(),
                credential.purpose()
            )
        )
            .thenReturn(Optional.of(entity));

        Optional<AccountActionCredential> result =
            adapter.findByDigestAndPurposeForUpdate(
                credential.digest(),
                credential.purpose()
            );

        assertThat(result).isPresent();
        AccountActionCredential restored =
            result.orElseThrow();
        assertThat(restored.id()).isEqualTo(credential.id());
        assertThat(restored.userId())
            .isEqualTo(credential.userId());
        assertThat(restored.purpose())
            .isEqualTo(credential.purpose());
        assertThat(restored.digest())
            .isEqualTo(credential.digest());
    }

    @Test
    void shouldDefensivelyCopyJpaDigest() {
        byte[] digest = new byte[
            AccountActionCredentialDigest
                .SHA_256_LENGTH_BYTES
        ];
        Arrays.fill(digest, (byte) 4);

        AccountActionCredentialJpaEntity entity =
            new AccountActionCredentialJpaEntity(
                UUID.randomUUID(),
                USER_ID,
                AccountActionCredentialPurpose
                    .PASSWORD_RECOVERY,
                digest,
                ISSUED_AT,
                ISSUED_AT.plus(Duration.ofMinutes(30)),
                null,
                null
            );

        digest[0] = 9;
        byte[] exposed = entity.getCredentialDigest();
        exposed[0] = 8;

        assertThat(entity.getCredentialDigest()[0])
            .isEqualTo((byte) 4);
    }

    private static AccountActionCredential
    activeCredential() {
        byte[] digest = new byte[
            AccountActionCredentialDigest
                .SHA_256_LENGTH_BYTES
        ];
        Arrays.fill(digest, (byte) 2);

        return AccountActionCredential.issue(
            AccountActionCredentialId.of(
                UUID.fromString(
                    "7b300984-9257-4ffd-97d2-f7c2175dc565"
                )
            ),
            USER_ID,
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION,
            AccountActionCredentialDigest.of(digest),
            ISSUED_AT,
            ISSUED_AT.plus(Duration.ofHours(24))
        );
    }
}
