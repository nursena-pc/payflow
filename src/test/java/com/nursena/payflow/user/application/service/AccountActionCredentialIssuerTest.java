package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.assertj.core.api.Assertions
    .assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
    .AccountActionCredentialGenerationPort;
import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialRepositoryPort;
import com.nursena.payflow.user.application.port.out
    .GeneratedAccountActionCredential;
import com.nursena.payflow.user.application.port.out
    .UserRepositoryPort;
import com.nursena.payflow.user.domain.exception
    .UserNotFoundException;
import com.nursena.payflow.user.domain.model
    .AccountActionCredential;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialDigest;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import com.nursena.payflow.user.domain.model
    .EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountActionCredentialIssuerTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "ae5a6f1a-f25e-4fc7-9202-ab256ba65e14"
        );

    private static final Instant NOW =
        Instant.parse("2026-08-05T12:00:00.123456Z");

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
    private UserRepositoryPort userRepository;

    @Mock
    private AccountActionCredentialRepositoryPort
        credentialRepository;

    @Mock
    private AccountActionCredentialGenerationPort
        credentialGeneration;

    @Mock
    private AccountActionCredentialDigestPort
        credentialDigest;

    private AccountActionCredentialIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new AccountActionCredentialIssuer(
            userRepository,
            credentialRepository,
            credentialGeneration,
            credentialDigest,
            new AccountActionCredentialLifetimePolicy(
                Duration.ofHours(24),
                Duration.ofMinutes(30)
            ),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldLockUserSupersedeAndIssueCredential() {
        User user = User.register(
            EmailAddress.of("user@example.com"),
            "$2a$12$hashed-password",
            NOW.minus(Duration.ofDays(1))
        );

        when(userRepository.findByIdForUpdate(USER_ID))
            .thenReturn(Optional.of(user));
        when(credentialGeneration.generate())
            .thenReturn(
                new GeneratedAccountActionCredential(
                    CREDENTIAL
                )
            );
        when(credentialDigest.digest(CREDENTIAL))
            .thenReturn(DIGEST);
        when(credentialRepository.save(
            any(AccountActionCredential.class)
        ))
            .thenAnswer(
                invocation -> invocation.getArgument(0)
            );

        IssuedAccountActionCredential result =
            issuer.issue(
                USER_ID,
                AccountActionCredentialPurpose
                    .PASSWORD_RECOVERY
            );

        assertThat(result.value())
            .isEqualTo(CREDENTIAL);
        assertThat(result.expiresAt())
            .isEqualTo(
                NOW.plus(Duration.ofMinutes(30))
            );
        assertThat(result.toString())
            .doesNotContain(CREDENTIAL);

        ArgumentCaptor<AccountActionCredential> captor =
            ArgumentCaptor.forClass(
                AccountActionCredential.class
            );
        verify(credentialRepository).save(
            captor.capture()
        );

        AccountActionCredential saved =
            captor.getValue();
        assertThat(saved.userId()).isEqualTo(USER_ID);
        assertThat(saved.purpose())
            .isEqualTo(
                AccountActionCredentialPurpose
                    .PASSWORD_RECOVERY
            );
        assertThat(saved.digest()).isEqualTo(DIGEST);
        assertThat(saved.issuedAt()).isEqualTo(NOW);

        InOrder order = inOrder(
            userRepository,
            credentialRepository,
            credentialGeneration,
            credentialDigest
        );
        order.verify(userRepository)
            .findByIdForUpdate(USER_ID);
        order.verify(credentialRepository)
            .supersedeUnresolved(
                USER_ID,
                AccountActionCredentialPurpose
                    .PASSWORD_RECOVERY,
                NOW
            );
        order.verify(credentialGeneration).generate();
        order.verify(credentialDigest)
            .digest(CREDENTIAL);
        order.verify(credentialRepository)
            .save(any(AccountActionCredential.class));
    }

    @Test
    void shouldStopBeforeCredentialWorkWhenUserMissing() {
        when(userRepository.findByIdForUpdate(USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            issuer.issue(
                USER_ID,
                AccountActionCredentialPurpose
                    .EMAIL_VERIFICATION
            )
        )
            .isInstanceOf(UserNotFoundException.class);

        verify(credentialRepository, never())
            .supersedeUnresolved(
                any(),
                any(),
                any()
            );
        verify(credentialGeneration, never()).generate();
        verify(credentialDigest, never()).digest(any());
    }
}
