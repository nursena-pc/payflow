package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.assertj.core.api.Assertions
    .assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialDigestPort;
import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialRepositoryPort;
import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountActionCredentialConsumerTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "fdbbc8a6-3d55-49ba-b0ee-bf4aa0ca6900"
        );

    private static final Instant NOW =
        Instant.parse("2026-08-05T12:00:00Z");

    private static final String CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    private static final AccountActionCredentialDigest
        DIGEST =
        AccountActionCredentialDigest.of(
            new byte[
                AccountActionCredentialDigest
                    .SHA_256_LENGTH_BYTES
            ]
        );

    @Mock
    private AccountActionCredentialRepositoryPort
        credentialRepository;

    @Mock
    private AccountActionCredentialDigestPort
        credentialDigest;

    private AccountActionCredentialConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AccountActionCredentialConsumer(
            credentialRepository,
            credentialDigest,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldLockConsumeAndReturnOwningUser() {
        when(credentialDigest.digest(CREDENTIAL))
            .thenReturn(DIGEST);
        when(
            credentialRepository
                .findByDigestAndPurposeForUpdate(
                    DIGEST,
                    AccountActionCredentialPurpose
                        .EMAIL_VERIFICATION
                )
        )
            .thenReturn(
                Optional.of(activeCredential())
            );
        when(credentialRepository.save(
            any(AccountActionCredential.class)
        ))
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        UUID result = consumer.consume(
            CREDENTIAL,
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION
        );

        assertThat(result).isEqualTo(USER_ID);

        ArgumentCaptor<AccountActionCredential> captor =
            ArgumentCaptor.forClass(
                AccountActionCredential.class
            );
        verify(credentialRepository).save(
            captor.capture()
        );
        assertThat(captor.getValue().consumedAt())
            .isEqualTo(NOW);
        assertThat(captor.getValue().supersededAt())
            .isNull();
    }

    @Test
    void shouldRejectUnknownCredentialGenerically() {
        when(credentialDigest.digest(CREDENTIAL))
            .thenReturn(DIGEST);
        when(
            credentialRepository
                .findByDigestAndPurposeForUpdate(
                    DIGEST,
                    AccountActionCredentialPurpose
                        .PASSWORD_RECOVERY
                )
        )
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            consumer.consume(
                CREDENTIAL,
                AccountActionCredentialPurpose
                    .PASSWORD_RECOVERY
            )
        )
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            )
            .hasMessage(
                "Account action credential is invalid."
            );

        verify(credentialRepository, never())
            .save(any());
    }

    @Test
    void shouldRejectExpiredCredentialWithoutSaving() {
        AccountActionCredential expired =
            AccountActionCredential.issue(
                AccountActionCredentialId.of(
                    UUID.randomUUID()
                ),
                USER_ID,
                AccountActionCredentialPurpose
                    .EMAIL_VERIFICATION,
                DIGEST,
                NOW.minus(Duration.ofHours(2)),
                NOW.minus(Duration.ofHours(1))
            );

        when(credentialDigest.digest(CREDENTIAL))
            .thenReturn(DIGEST);
        when(
            credentialRepository
                .findByDigestAndPurposeForUpdate(
                    DIGEST,
                    AccountActionCredentialPurpose
                        .EMAIL_VERIFICATION
                )
        )
            .thenReturn(Optional.of(expired));

        assertThatThrownBy(() ->
            consumer.consume(
                CREDENTIAL,
                AccountActionCredentialPurpose
                    .EMAIL_VERIFICATION
            )
        )
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            );

        verify(credentialRepository, never())
            .save(any());
    }

    private static AccountActionCredential
    activeCredential() {
        return AccountActionCredential.issue(
            AccountActionCredentialId.of(
                UUID.randomUUID()
            ),
            USER_ID,
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION,
            DIGEST,
            NOW.minus(Duration.ofMinutes(5)),
            NOW.plus(Duration.ofHours(1))
        );
    }
}
