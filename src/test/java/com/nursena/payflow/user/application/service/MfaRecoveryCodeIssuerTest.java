package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.nursena.payflow.user.application.port.out.GeneratedMfaRecoveryCode;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeDigestPort;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeGenerationPort;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeRepositoryPort;
import com.nursena.payflow.user.domain.model.MfaRecoveryCode;
import com.nursena.payflow.user.domain.model.MfaRecoveryCodeDigest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MfaRecoveryCodeIssuerTest {

    private static final UUID USER_ID =
        UUID.fromString("d48ab875-1a71-4de0-a57b-d3bb0d384677");
    private static final Instant NOW =
        Instant.parse("2026-08-09T12:00:00Z");

    @Mock MfaRecoveryCodeGenerationPort generationPort;
    @Mock MfaRecoveryCodeDigestPort digestPort;
    @Mock MfaRecoveryCodeRepositoryPort repository;

    private MfaRecoveryCodeIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new MfaRecoveryCodeIssuer(
            generationPort,
            digestPort,
            repository
        );
    }

    @Test
    void shouldIssueTenPlaintextCodesWhilePersistingOnlyDigests()
        throws Exception {
        AtomicInteger sequence = new AtomicInteger();
        when(generationPort.generate()).thenAnswer(invocation ->
            new GeneratedMfaRecoveryCode(code(sequence.getAndIncrement()))
        );
        when(digestPort.digest(any())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            return MfaRecoveryCodeDigest.of(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII))
            );
        });
        when(repository.saveAll(any())).thenAnswer(invocation -> {
            List<MfaRecoveryCode> values = invocation.getArgument(0);
            return List.copyOf(values);
        });

        List<String> plaintext = issuer.issue(USER_ID, NOW);

        assertThat(plaintext)
            .hasSize(10)
            .doesNotHaveDuplicates();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MfaRecoveryCode>> captor =
            ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        assertThat(captor.getValue())
            .hasSize(10)
            .allSatisfy(code -> {
                assertThat(code.userId()).isEqualTo(USER_ID);
                assertThat(code.createdAt()).isEqualTo(NOW);
                assertThat(code.consumedAt()).isNull();
                assertThat(code.digest().value()).hasSize(32);
            });
    }

    @Test
    void shouldRetryDuplicateDigestsWithinGeneratedSet() {
        AtomicInteger sequence = new AtomicInteger();
        when(generationPort.generate()).thenAnswer(invocation ->
            new GeneratedMfaRecoveryCode(code(sequence.getAndIncrement()))
        );
        MfaRecoveryCodeDigest duplicate =
            MfaRecoveryCodeDigest.of(new byte[32]);
        AtomicInteger digests = new AtomicInteger();
        when(digestPort.digest(any())).thenAnswer(invocation -> {
            int index = digests.getAndIncrement();
            if (index < 2) {
                return duplicate;
            }
            byte[] value = new byte[32];
            value[0] = (byte) index;
            return MfaRecoveryCodeDigest.of(value);
        });
        when(repository.saveAll(any())).thenAnswer(invocation -> {
            List<MfaRecoveryCode> values = invocation.getArgument(0);
            return List.copyOf(values);
        });

        assertThat(issuer.issue(USER_ID, NOW))
            .hasSize(10)
            .doesNotHaveDuplicates();
    }

    @Test
    void shouldFailClosedWhenPersistenceReturnsIncompleteSet() {
        AtomicInteger sequence = new AtomicInteger();
        when(generationPort.generate()).thenAnswer(invocation ->
            new GeneratedMfaRecoveryCode(code(sequence.getAndIncrement()))
        );
        AtomicInteger digestSequence = new AtomicInteger();
        when(digestPort.digest(any())).thenAnswer(invocation -> {
            byte[] value = new byte[32];
            value[0] = (byte) digestSequence.getAndIncrement();
            return MfaRecoveryCodeDigest.of(value);
        });
        when(repository.saveAll(any())).thenReturn(List.of());

        assertThatThrownBy(() -> issuer.issue(USER_ID, NOW))
            .isInstanceOf(IllegalStateException.class);
    }

    private static String code(int index) {
        return String.format("Rc%020d", index);
    }
}
