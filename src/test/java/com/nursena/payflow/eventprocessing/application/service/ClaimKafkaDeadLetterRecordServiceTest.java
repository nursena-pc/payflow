package com.nursena.payflow.eventprocessing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayRepositoryPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClaimKafkaDeadLetterRecordServiceTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001303"
        );

    private static final String WORKER_ID =
        "replay-worker-1";

    private static final Duration LEASE_DURATION =
        Duration.ofSeconds(30);

    private static final int MAX_ATTEMPTS = 3;

    private static final Instant CLAIMED_AT =
        Instant.parse(
            "2026-07-21T19:05:00.123456Z"
        );

    @Mock
    private KafkaDeadLetterReplayRepositoryPort
        repository;

    private ClaimKafkaDeadLetterRecordService
        service;

    @BeforeEach
    void setUp() {
        service =
            new ClaimKafkaDeadLetterRecordService(
                repository,
                WORKER_ID,
                LEASE_DURATION,
                MAX_ATTEMPTS,
                Clock.fixed(
                    CLAIMED_AT,
                    ZoneOffset.UTC
                )
            );
    }

    @Test
    void shouldReturnClaimedRecord() {
        KafkaDeadLetterRecord record =
            claimedRecord();

        when(
            repository.tryClaim(
                RECORD_ID,
                WORKER_ID,
                CLAIMED_AT,
                LEASE_DURATION,
                MAX_ATTEMPTS
            )
        )
            .thenReturn(
                Optional.of(record)
            );

        ClaimKafkaDeadLetterRecordResult result =
            service.claim(
                new ClaimKafkaDeadLetterRecordCommand(
                    RECORD_ID
                )
            );

        assertThat(result.isClaimed())
            .isTrue();

        assertThat(result.record())
            .isSameAs(record);

        verify(repository)
            .tryClaim(
                RECORD_ID,
                WORKER_ID,
                CLAIMED_AT,
                LEASE_DURATION,
                MAX_ATTEMPTS
            );
    }

    @Test
    void shouldReturnNotClaimableWhenRepositoryCannotClaim() {
        when(
            repository.tryClaim(
                RECORD_ID,
                WORKER_ID,
                CLAIMED_AT,
                LEASE_DURATION,
                MAX_ATTEMPTS
            )
        )
            .thenReturn(
                Optional.empty()
            );

        ClaimKafkaDeadLetterRecordResult result =
            service.claim(
                new ClaimKafkaDeadLetterRecordCommand(
                    RECORD_ID
                )
            );

        assertThat(result.isClaimed())
            .isFalse();

        assertThat(result.record())
            .isNull();
    }

    @Test
    void shouldRejectNullCommand() {
        assertThatThrownBy(
            () -> service.claim(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "command must not be null"
            );
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        Clock clock =
            Clock.fixed(
                CLAIMED_AT,
                ZoneOffset.UTC
            );

        assertThatThrownBy(
            () ->
                new ClaimKafkaDeadLetterRecordService(
                    repository,
                    " ",
                    LEASE_DURATION,
                    MAX_ATTEMPTS,
                    clock
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "workerId must not be blank."
            );

        assertThatThrownBy(
            () ->
                new ClaimKafkaDeadLetterRecordService(
                    repository,
                    WORKER_ID,
                    Duration.ZERO,
                    MAX_ATTEMPTS,
                    clock
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "leaseDuration must be positive."
            );

        assertThatThrownBy(
            () ->
                new ClaimKafkaDeadLetterRecordService(
                    repository,
                    WORKER_ID,
                    LEASE_DURATION,
                    0,
                    clock
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "maxReplayAttempts must be "
                    + "positive."
            );
    }

    private static KafkaDeadLetterRecord
    claimedRecord() {
        Instant receivedAt =
            Instant.parse(
                "2026-07-21T19:00:00Z"
            );

        return new KafkaDeadLetterRecord(
            RECORD_ID,
            "wallet.transfer.completed.dlt",
            0,
            25L,
            "wallet.transfer.completed",
            0,
            10L,
            "payflow-transfer-completed-audit-v1",
            "transaction-id",
            "{}",
            "IllegalStateException",
            "Temporary failure.",
            KafkaDeadLetterRecordStatus.REPLAYING,
            1,
            receivedAt,
            CLAIMED_AT,
            WORKER_ID,
            CLAIMED_AT.plus(
                LEASE_DURATION
            ),
            null,
            RECORD_ID,
            0
        );
    }
}
