package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsCommand;
import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsResult;
import com.nursena.payflow.user.application.port.out.AccessTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.GeneratedAccessToken;
import com.nursena.payflow.user.application.port.out.GeneratedRefreshToken;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidRefreshTokenException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyId;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.RefreshTokenRecordId;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class RotateRefreshCredentialsServiceTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final RefreshTokenFamilyId
        FAMILY_ID =
        RefreshTokenFamilyId.of(
            UUID.fromString(
                "f89de08d-3035-407d-bbfd-e49eec447177"
            )
        );

    private static final RefreshTokenRecordId
        CURRENT_RECORD_ID =
        RefreshTokenRecordId.of(
            UUID.fromString(
                "0d559bfd-05b6-43aa-8822-49b83eea3f1b"
            )
        );

    private static final RefreshTokenRecordId
        EXISTING_SUCCESSOR_ID =
        RefreshTokenRecordId.of(
            UUID.fromString(
                "55667aa8-fd92-4ddb-836b-f83362e5932c"
            )
        );

    private static final Instant NOW =
        Instant.parse(
            "2026-07-28T12:00:00Z"
        );

    private static final Instant FAMILY_CREATED_AT =
        NOW.minus(
            Duration.ofDays(1)
        );

    private static final Instant FAMILY_EXPIRES_AT =
        NOW.plus(
            Duration.ofDays(30)
        );

    private static final Instant CURRENT_ISSUED_AT =
        NOW.minus(
            Duration.ofHours(1)
        );

    private static final Instant CURRENT_EXPIRES_AT =
        NOW.plus(
            Duration.ofDays(6)
        );

    private static final Instant ACCESS_EXPIRES_AT =
        NOW.plus(
            Duration.ofMinutes(15)
        );

    private static final Instant SUCCESSOR_EXPIRES_AT =
        NOW.plus(
            Duration.ofDays(7)
        );

    private static final String CURRENT_TOKEN =
        "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA";

    private static final String SUCCESSOR_TOKEN =
        "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8";

    private static final String ACCESS_TOKEN =
        "rotated.jwt.token";

    private static final RefreshTokenDigest
        CURRENT_DIGEST =
        digest((byte) 7);

    private static final RefreshTokenDigest
        SUCCESSOR_DIGEST =
        digest((byte) 9);

    @Mock
    private RefreshTokenDigestPort
        refreshTokenDigest;

    @Mock
    private RefreshTokenRecordRepositoryPort
        recordRepository;

    @Mock
    private RefreshTokenFamilyRepositoryPort
        familyRepository;

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private RefreshTokenGenerationPort
        refreshTokenGeneration;

    @Mock
    private AccessTokenGenerationPort
        accessTokenGeneration;

    private RefreshSessionLifetimePolicy
        lifetimePolicy;

    private Clock clock;

    private RotateRefreshCredentialsService service;

    @BeforeEach
    void setUp() {
        lifetimePolicy =
            new RefreshSessionLifetimePolicy(
                Duration.ofDays(7),
                Duration.ofDays(30)
            );

        clock =
            Clock.fixed(
                NOW,
                ZoneOffset.UTC
            );

        service =
            new RotateRefreshCredentialsService(
                refreshTokenDigest,
                recordRepository,
                familyRepository,
                userRepository,
                refreshTokenGeneration,
                accessTokenGeneration,
                lifetimePolicy,
                clock
            );
    }

    @Test
    void shouldRotateActiveRefreshCredentials() {
        RefreshTokenFamily family =
            activeFamily();

        RefreshTokenRecord currentRecord =
            activeRecord(family);

        User user =
            activeUser();

        stubValidRotation(
            family,
            currentRecord,
            user
        );

        when(recordRepository.save(
            any(RefreshTokenRecord.class)
        ))
            .thenAnswer(
                invocation ->
                    invocation.getArgument(0)
            );

        when(accessTokenGeneration.generate(user))
            .thenReturn(
                new GeneratedAccessToken(
                    ACCESS_TOKEN,
                    ACCESS_EXPIRES_AT
                )
            );

        RotateRefreshCredentialsResult result =
            service.rotate(
                command()
            );

        assertThat(result.accessToken())
            .isEqualTo(
                ACCESS_TOKEN
            );

        assertThat(result.expiresAt())
            .isEqualTo(
                ACCESS_EXPIRES_AT
            );

        assertThat(result.refreshToken())
            .isEqualTo(
                SUCCESSOR_TOKEN
            );

        assertThat(
            result.refreshTokenExpiresAt()
        )
            .isEqualTo(
                SUCCESSOR_EXPIRES_AT
            );

        ArgumentCaptor<RefreshTokenRecord>
            recordCaptor =
            ArgumentCaptor.forClass(
                RefreshTokenRecord.class
            );

        verify(recordRepository, times(2))
            .save(
                recordCaptor.capture()
            );

        List<RefreshTokenRecord> savedRecords =
            recordCaptor.getAllValues();

        RefreshTokenRecord successor =
            savedRecords.get(0);

        RefreshTokenRecord consumedCurrent =
            savedRecords.get(1);

        assertThat(successor.id())
            .isNotEqualTo(
                CURRENT_RECORD_ID
            );

        assertThat(successor.familyId())
            .isEqualTo(
                FAMILY_ID
            );

        assertThat(successor.digest())
            .isEqualTo(
                SUCCESSOR_DIGEST
            );

        assertThat(successor.issuedAt())
            .isEqualTo(
                NOW
            );

        assertThat(successor.expiresAt())
            .isEqualTo(
                SUCCESSOR_EXPIRES_AT
            );

        assertThat(successor.isConsumed())
            .isFalse();

        assertThat(consumedCurrent.id())
            .isEqualTo(
                CURRENT_RECORD_ID
            );

        assertThat(consumedCurrent.consumedAt())
            .isEqualTo(
                NOW
            );

        assertThat(consumedCurrent.successorId())
            .isEqualTo(
                successor.id()
            );

        InOrder order =
            inOrder(
                refreshTokenDigest,
                recordRepository,
                familyRepository,
                userRepository,
                refreshTokenGeneration,
                accessTokenGeneration
            );

        order.verify(refreshTokenDigest)
            .digest(CURRENT_TOKEN);

        order.verify(recordRepository)
            .findByDigestForUpdate(
                CURRENT_DIGEST
            );

        order.verify(familyRepository)
            .findByIdForUpdate(
                FAMILY_ID
            );

        order.verify(userRepository)
            .findById(USER_ID);

        order.verify(refreshTokenGeneration)
            .generate();

        order.verify(refreshTokenDigest)
            .digest(SUCCESSOR_TOKEN);

        order.verify(
            recordRepository,
            times(2)
        )
            .save(
                any(RefreshTokenRecord.class)
            );

        order.verify(accessTokenGeneration)
            .generate(user);
    }

    @Test
    void shouldDeclareWriteTransaction()
        throws NoSuchMethodException {

        Method rotate =
            RotateRefreshCredentialsService.class
                .getDeclaredMethod(
                    "rotate",
                    RotateRefreshCredentialsCommand.class
                );

        Transactional transactional =
            rotate.getAnnotation(
                Transactional.class
            );

        assertThat(transactional)
            .isNotNull();

        assertThat(transactional.readOnly())
            .isFalse();
    }

    @Test
    void shouldRejectUnknownRefreshToken() {
        when(refreshTokenDigest.digest(
            CURRENT_TOKEN
        ))
            .thenReturn(
                CURRENT_DIGEST
            );

        when(recordRepository
            .findByDigestForUpdate(
                CURRENT_DIGEST
            ))
            .thenReturn(
                Optional.empty()
            );

        assertThatThrownBy(() ->
            service.rotate(command())
        )
            .isInstanceOf(
                InvalidRefreshTokenException.class
            )
            .hasMessage(
                "Refresh token is invalid."
            );

        verifyNoInteractions(
            familyRepository,
            userRepository,
            refreshTokenGeneration,
            accessTokenGeneration
        );

        verify(recordRepository, never())
            .save(
                any(RefreshTokenRecord.class)
            );
    }

    @Test
    void shouldRejectConsumedRefreshToken() {
        RefreshTokenFamily family =
            activeFamily();

        RefreshTokenRecord consumedRecord =
            activeRecord(family)
                .consume(
                    EXISTING_SUCCESSOR_ID,
                    NOW.minusSeconds(1),
                    family
                );

        stubLockedSession(
            family,
            consumedRecord
        );

        assertThatThrownBy(() ->
            service.rotate(command())
        )
            .isInstanceOf(
                InvalidRefreshTokenException.class
            );

        verifyNoInteractions(
            userRepository,
            refreshTokenGeneration,
            accessTokenGeneration
        );

        verify(recordRepository, never())
            .save(
                any(RefreshTokenRecord.class)
            );
    }

    @Test
    void shouldRejectExpiredRefreshToken() {
        RefreshTokenFamily family =
            activeFamily();

        RefreshTokenRecord expiredRecord =
            RefreshTokenRecord.issue(
                CURRENT_RECORD_ID,
                family,
                CURRENT_DIGEST,
                CURRENT_ISSUED_AT,
                NOW
            );

        stubLockedSession(
            family,
            expiredRecord
        );

        assertThatThrownBy(() ->
            service.rotate(command())
        )
            .isInstanceOf(
                InvalidRefreshTokenException.class
            );

        verifyNoInteractions(
            userRepository,
            refreshTokenGeneration,
            accessTokenGeneration
        );
    }

    @Test
    void shouldRejectUnavailableUser() {
        RefreshTokenFamily family =
            activeFamily();

        RefreshTokenRecord currentRecord =
            activeRecord(family);

        stubLockedSession(
            family,
            currentRecord
        );

        when(userRepository.findById(USER_ID))
            .thenReturn(
                Optional.of(
                    suspendedUser()
                )
            );

        assertThatThrownBy(() ->
            service.rotate(command())
        )
            .isInstanceOf(
                InvalidRefreshTokenException.class
            );

        verifyNoInteractions(
            refreshTokenGeneration,
            accessTokenGeneration
        );

        verify(recordRepository, never())
            .save(
                any(RefreshTokenRecord.class)
            );
    }

    @Test
    void shouldStopWhenSuccessorPersistenceFails() {
        RefreshTokenFamily family =
            activeFamily();

        RefreshTokenRecord currentRecord =
            activeRecord(family);

        User user =
            activeUser();

        stubValidRotation(
            family,
            currentRecord,
            user
        );

        when(recordRepository.save(
            any(RefreshTokenRecord.class)
        ))
            .thenThrow(
                new IllegalStateException(
                    "successor persistence failed"
                )
            );

        assertThatThrownBy(() ->
            service.rotate(command())
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "successor persistence failed"
            );

        verify(recordRepository, times(1))
            .save(
                any(RefreshTokenRecord.class)
            );

        verifyNoInteractions(
            accessTokenGeneration
        );
    }

    @Test
    void shouldStopWhenPredecessorPersistenceFails() {
        RefreshTokenFamily family =
            activeFamily();

        RefreshTokenRecord currentRecord =
            activeRecord(family);

        User user =
            activeUser();

        stubValidRotation(
            family,
            currentRecord,
            user
        );

        AtomicInteger saveAttempts =
            new AtomicInteger();

        when(recordRepository.save(
            any(RefreshTokenRecord.class)
        ))
            .thenAnswer(invocation -> {
                if (
                    saveAttempts.getAndIncrement()
                        == 0
                ) {
                    return invocation.getArgument(0);
                }

                throw new IllegalStateException(
                    "predecessor persistence failed"
                );
            });

        assertThatThrownBy(() ->
            service.rotate(command())
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "predecessor persistence failed"
            );

        verify(recordRepository, times(2))
            .save(
                any(RefreshTokenRecord.class)
            );

        verifyNoInteractions(
            accessTokenGeneration
        );
    }

    @Test
    void shouldGenerateAccessTokenAfterBothRecordsPersist() {
        RefreshTokenFamily family =
            activeFamily();

        RefreshTokenRecord currentRecord =
            activeRecord(family);

        User user =
            activeUser();

        stubValidRotation(
            family,
            currentRecord,
            user
        );

        when(recordRepository.save(
            any(RefreshTokenRecord.class)
        ))
            .thenAnswer(
                invocation ->
                    invocation.getArgument(0)
            );

        when(accessTokenGeneration.generate(user))
            .thenThrow(
                new IllegalStateException(
                    "access-token generation failed"
                )
            );

        assertThatThrownBy(() ->
            service.rotate(command())
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "access-token generation failed"
            );

        InOrder order =
            inOrder(
                recordRepository,
                accessTokenGeneration
            );

        order.verify(
            recordRepository,
            times(2)
        )
            .save(
                any(RefreshTokenRecord.class)
            );

        order.verify(accessTokenGeneration)
            .generate(user);
    }

    private void stubValidRotation(
        RefreshTokenFamily family,
        RefreshTokenRecord currentRecord,
        User user
    ) {
        stubLockedSession(
            family,
            currentRecord
        );

        when(userRepository.findById(USER_ID))
            .thenReturn(
                Optional.of(user)
            );

        when(refreshTokenGeneration.generate())
            .thenReturn(
                new GeneratedRefreshToken(
                    SUCCESSOR_TOKEN
                )
            );

        when(refreshTokenDigest.digest(
            SUCCESSOR_TOKEN
        ))
            .thenReturn(
                SUCCESSOR_DIGEST
            );
    }

    private void stubLockedSession(
        RefreshTokenFamily family,
        RefreshTokenRecord currentRecord
    ) {
        when(refreshTokenDigest.digest(
            CURRENT_TOKEN
        ))
            .thenReturn(
                CURRENT_DIGEST
            );

        when(recordRepository
            .findByDigestForUpdate(
                CURRENT_DIGEST
            ))
            .thenReturn(
                Optional.of(currentRecord)
            );

        when(familyRepository
            .findByIdForUpdate(
                FAMILY_ID
            ))
            .thenReturn(
                Optional.of(family)
            );
    }

    private static RotateRefreshCredentialsCommand
    command() {
        return new RotateRefreshCredentialsCommand(
            CURRENT_TOKEN
        );
    }

    private static RefreshTokenFamily
    activeFamily() {
        return RefreshTokenFamily.create(
            FAMILY_ID,
            USER_ID,
            FAMILY_CREATED_AT,
            FAMILY_EXPIRES_AT
        );
    }

    private static RefreshTokenRecord activeRecord(
        RefreshTokenFamily family
    ) {
        return RefreshTokenRecord.issue(
            CURRENT_RECORD_ID,
            family,
            CURRENT_DIGEST,
            CURRENT_ISSUED_AT,
            CURRENT_EXPIRES_AT
        );
    }

    private static User activeUser() {
        return user(UserStatus.ACTIVE);
    }

    private static User suspendedUser() {
        return user(UserStatus.SUSPENDED);
    }

    private static User user(
        UserStatus status
    ) {
        return User.rehydrate(
            USER_ID,
            EmailAddress.of(
                "nursena@example.com"
            ),
            "hashed-password",
            UserRole.USER,
            status,
            FAMILY_CREATED_AT,
            FAMILY_CREATED_AT
        );
    }

    private static RefreshTokenDigest digest(
        byte fillValue
    ) {
        byte[] bytes =
            new byte[
                RefreshTokenDigest
                    .SHA_256_LENGTH_BYTES
            ];

        Arrays.fill(
            bytes,
            fillValue
        );

        return RefreshTokenDigest.of(
            bytes
        );
    }
}
