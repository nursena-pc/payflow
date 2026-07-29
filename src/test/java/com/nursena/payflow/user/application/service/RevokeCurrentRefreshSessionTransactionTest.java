package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyId;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.RefreshTokenRecordId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class RevokeCurrentRefreshSessionTransactionTest {

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
        RECORD_ID =
        RefreshTokenRecordId.of(
            UUID.fromString(
                "0d559bfd-05b6-43aa-8822-49b83eea3f1b"
            )
        );

    private static final RefreshTokenRecordId
        SUCCESSOR_ID =
        RefreshTokenRecordId.of(
            UUID.fromString(
                "55667aa8-fd92-4ddb-836b-f83362e5932c"
            )
        );

    private static final Instant NOW =
        Instant.parse(
            "2026-07-29T12:00:00Z"
        );

    private static final Instant FAMILY_CREATED_AT =
        NOW.minus(
            Duration.ofDays(1)
        );

    private static final Instant FAMILY_EXPIRES_AT =
        NOW.plus(
            Duration.ofDays(30)
        );

    private static final Instant RECORD_ISSUED_AT =
        NOW.minus(
            Duration.ofHours(2)
        );

    private static final Instant RECORD_EXPIRES_AT =
        NOW.plus(
            Duration.ofDays(6)
        );

    private static final RefreshTokenDigest DIGEST =
        digest((byte) 7);

    @Mock
    private RefreshTokenRecordRepositoryPort
        recordRepository;

    @Mock
    private RefreshTokenFamilyRepositoryPort
        familyRepository;

    private RevokeCurrentRefreshSessionTransaction
        transaction;

    @BeforeEach
    void setUp() {
        transaction =
            new RevokeCurrentRefreshSessionTransaction(
                recordRepository,
                familyRepository,
                Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
                )
            );
    }

    @Test
    void shouldRevokeActiveFamily() {
        RefreshTokenFamily family =
            activeFamily();

        RefreshTokenRecord record =
            activeRecord(family);

        stubLockedSession(
            family,
            record
        );

        transaction.revoke(
            DIGEST
        );

        ArgumentCaptor<RefreshTokenFamily>
            familyCaptor =
            ArgumentCaptor.forClass(
                RefreshTokenFamily.class
            );

        InOrder order =
            inOrder(
                recordRepository,
                familyRepository
            );

        order.verify(recordRepository)
            .findByDigestForUpdate(
                DIGEST
            );

        order.verify(familyRepository)
            .findByIdForUpdate(
                FAMILY_ID
            );

        order.verify(familyRepository)
            .save(
                familyCaptor.capture()
            );

        RefreshTokenFamily savedFamily =
            familyCaptor.getValue();

        assertThat(savedFamily.id())
            .isEqualTo(
                FAMILY_ID
            );

        assertThat(savedFamily.revokedAt())
            .isEqualTo(
                NOW
            );

        assertThat(savedFamily.revocationReason())
            .isEqualTo(
                RefreshTokenFamilyRevocationReason
                    .CURRENT_SESSION_LOGOUT
            );
    }

    @Test
    void shouldDeclareWriteTransaction()
        throws NoSuchMethodException {

        Method method =
            RevokeCurrentRefreshSessionTransaction
                .class
                .getDeclaredMethod(
                    "revoke",
                    RefreshTokenDigest.class
                );

        assertThat(
            method.getAnnotation(
                Transactional.class
            )
        ).isNotNull();
    }

    @Test
    void shouldCompleteForUnknownRefreshToken() {
        when(recordRepository
            .findByDigestForUpdate(
                DIGEST
            ))
            .thenReturn(
                Optional.empty()
            );

        transaction.revoke(
            DIGEST
        );

        verifyNoInteractions(
            familyRepository
        );
    }

    @Test
    void shouldCompleteWhenFamilyIsMissing() {
        RefreshTokenFamily family =
            activeFamily();

        RefreshTokenRecord record =
            activeRecord(family);

        when(recordRepository
            .findByDigestForUpdate(
                DIGEST
            ))
            .thenReturn(
                Optional.of(record)
            );

        when(familyRepository
            .findByIdForUpdate(
                FAMILY_ID
            ))
            .thenReturn(
                Optional.empty()
            );

        transaction.revoke(
            DIGEST
        );

        verify(familyRepository, never())
            .save(
                any(RefreshTokenFamily.class)
            );
    }

    @Test
    void shouldRevokeActiveFamilyForConsumedRecord() {
        RefreshTokenFamily family =
            activeFamily();

        RefreshTokenRecord record =
            consumedRecord(family);

        stubLockedSession(
            family,
            record
        );

        transaction.revoke(
            DIGEST
        );

        verify(familyRepository)
            .save(
                any(RefreshTokenFamily.class)
            );
    }

    @Test
    void shouldRevokeActiveFamilyForExpiredRecord() {
        RefreshTokenFamily family =
            activeFamily();

        RefreshTokenRecord record =
            expiredRecord(family);

        stubLockedSession(
            family,
            record
        );

        transaction.revoke(
            DIGEST
        );

        verify(familyRepository)
            .save(
                any(RefreshTokenFamily.class)
            );
    }

    @Test
    void shouldPreserveExistingFamilyRevocation() {
        RefreshTokenFamily family =
            revokedFamily();

        RefreshTokenRecord record =
            rehydratedRecord(family);

        stubLockedSession(
            family,
            record
        );

        transaction.revoke(
            DIGEST
        );

        verify(familyRepository, never())
            .save(
                any(RefreshTokenFamily.class)
            );

        assertThat(family.revocationReason())
            .isEqualTo(
                RefreshTokenFamilyRevocationReason
                    .REUSE_DETECTED
            );
    }

    @Test
    void shouldNotRevokeExpiredFamily() {
        RefreshTokenFamily family =
            expiredFamily();

        RefreshTokenRecord record =
            activeAtIssueTimeRecord(family);

        stubLockedSession(
            family,
            record
        );

        transaction.revoke(
            DIGEST
        );

        verify(familyRepository, never())
            .save(
                any(RefreshTokenFamily.class)
            );
    }

    @Test
    void shouldPropagateFamilyPersistenceFailure() {
        RefreshTokenFamily family =
            activeFamily();

        RefreshTokenRecord record =
            activeRecord(family);

        stubLockedSession(
            family,
            record
        );

        when(familyRepository.save(
            any(RefreshTokenFamily.class)
        ))
            .thenThrow(
                new IllegalStateException(
                    "family persistence failed"
                )
            );

        assertThatThrownBy(() ->
            transaction.revoke(
                DIGEST
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "family persistence failed"
            );
    }

    @Test
    void shouldRequireDigest() {
        assertThatThrownBy(() ->
            transaction.revoke(
                null
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "digest must not be null"
            );

        verifyNoInteractions(
            recordRepository,
            familyRepository
        );
    }

    private void stubLockedSession(
        RefreshTokenFamily family,
        RefreshTokenRecord record
    ) {
        when(recordRepository
            .findByDigestForUpdate(
                DIGEST
            ))
            .thenReturn(
                Optional.of(record)
            );

        when(familyRepository
            .findByIdForUpdate(
                FAMILY_ID
            ))
            .thenReturn(
                Optional.of(family)
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

    private static RefreshTokenFamily
    revokedFamily() {
        return RefreshTokenFamily.rehydrate(
            FAMILY_ID,
            USER_ID,
            FAMILY_CREATED_AT,
            FAMILY_EXPIRES_AT,
            NOW.minus(
                Duration.ofMinutes(5)
            ),
            RefreshTokenFamilyRevocationReason
                .REUSE_DETECTED
        );
    }

    private static RefreshTokenFamily
    expiredFamily() {
        return RefreshTokenFamily.create(
            FAMILY_ID,
            USER_ID,
            FAMILY_CREATED_AT,
            NOW.minus(
                Duration.ofMinutes(1)
            )
        );
    }

    private static RefreshTokenRecord activeRecord(
        RefreshTokenFamily family
    ) {
        return RefreshTokenRecord.issue(
            RECORD_ID,
            family,
            DIGEST,
            RECORD_ISSUED_AT,
            RECORD_EXPIRES_AT
        );
    }

    private static RefreshTokenRecord consumedRecord(
        RefreshTokenFamily family
    ) {
        return activeRecord(family)
            .consume(
                SUCCESSOR_ID,
                NOW.minus(
                    Duration.ofMinutes(1)
                ),
                family
            );
    }

    private static RefreshTokenRecord expiredRecord(
        RefreshTokenFamily family
    ) {
        return RefreshTokenRecord.issue(
            RECORD_ID,
            family,
            DIGEST,
            RECORD_ISSUED_AT,
            NOW.minus(
                Duration.ofMinutes(1)
            )
        );
    }

    private static RefreshTokenRecord rehydratedRecord(
        RefreshTokenFamily family
    ) {
        return RefreshTokenRecord.rehydrate(
            RECORD_ID,
            FAMILY_ID,
            DIGEST,
            RECORD_ISSUED_AT,
            RECORD_EXPIRES_AT,
            null,
            null,
            family
        );
    }

    private static RefreshTokenRecord
    activeAtIssueTimeRecord(
        RefreshTokenFamily family
    ) {
        Instant issuedAt =
            family.expiresAt()
                .minus(
                    Duration.ofHours(1)
                );

        return RefreshTokenRecord.issue(
            RECORD_ID,
            family,
            DIGEST,
            issuedAt,
            family.expiresAt()
                .minus(
                    Duration.ofMinutes(1)
                )
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
