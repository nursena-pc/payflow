package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.AuthenticatedUserResult;
import com.nursena.payflow.user.application.port.out.AccessTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.GeneratedAccessToken;
import com.nursena.payflow.user.application.port.out.GeneratedRefreshToken;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticationCredentialIssuerTest {

    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("f0d225e8-c36f-46d3-a410-dd68d2680826");

    @Mock RefreshTokenGenerationPort generation;
    @Mock RefreshTokenDigestPort digestPort;
    @Mock RefreshTokenFamilyRepositoryPort familyRepository;
    @Mock RefreshTokenRecordRepositoryPort recordRepository;
    @Mock AccessTokenGenerationPort accessTokenGeneration;

    private AuthenticationCredentialIssuer issuer;
    private User user;

    @BeforeEach
    void setUp() {
        issuer = new AuthenticationCredentialIssuer(
            generation,
            digestPort,
            familyRepository,
            recordRepository,
            accessTokenGeneration,
            new RefreshSessionLifetimePolicy(Duration.ofDays(7), Duration.ofDays(30))
        );
        user = User.rehydrate(
            USER_ID,
            EmailAddress.of("user@example.com"),
            "hash",
            UserRole.USER,
            UserStatus.ACTIVE,
            NOW,
            NOW,
            NOW
        );
    }

    @Test
    void shouldIssueAccessAndRefreshCredentials() {
        stubPersistence();
        when(accessTokenGeneration.generate(user))
            .thenReturn(new GeneratedAccessToken("access", NOW.plusSeconds(900)));

        AuthenticatedUserResult result = issuer.issue(user, NOW);

        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(result.refreshTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void shouldPersistRefreshFamilyForOwningUser() {
        stubPersistence();
        when(accessTokenGeneration.generate(user))
            .thenReturn(new GeneratedAccessToken("access", NOW.plusSeconds(900)));
        issuer.issue(user, NOW);
        org.mockito.Mockito.verify(familyRepository).save(
            org.mockito.ArgumentMatchers.argThat(family ->
                family.userId().equals(USER_ID)
                    && family.createdAt().equals(NOW)
                    && family.expiresAt().equals(NOW.plus(Duration.ofDays(30)))
            )
        );
    }

    @Test
    void shouldPersistOnlyRefreshDigest() {
        RefreshTokenDigest digest = refreshDigest();
        when(generation.generate()).thenReturn(new GeneratedRefreshToken("refresh"));
        when(digestPort.digest("refresh")).thenReturn(digest);
        when(familyRepository.save(any(RefreshTokenFamily.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(recordRepository.save(any(RefreshTokenRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(accessTokenGeneration.generate(user))
            .thenReturn(new GeneratedAccessToken("access", NOW.plusSeconds(900)));

        issuer.issue(user, NOW);

        org.mockito.Mockito.verify(recordRepository).save(
            org.mockito.ArgumentMatchers.argThat(record -> record.digest().equals(digest))
        );
    }

    private void stubPersistence() {
        when(generation.generate()).thenReturn(new GeneratedRefreshToken("refresh"));
        when(digestPort.digest("refresh")).thenReturn(refreshDigest());
        when(familyRepository.save(any(RefreshTokenFamily.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(recordRepository.save(any(RefreshTokenRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static RefreshTokenDigest refreshDigest() {
        byte[] value = new byte[RefreshTokenDigest.SHA_256_LENGTH_BYTES];
        Arrays.fill(value, (byte) 7);
        return RefreshTokenDigest.of(value);
    }
}
