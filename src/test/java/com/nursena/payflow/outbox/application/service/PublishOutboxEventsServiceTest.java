package com.nursena.payflow.outbox.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyString;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.outbox.application.policy.OutboxRetryPolicy;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsCommand;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsResult;
import com.nursena.payflow.outbox.application.port.out.OutboxEventClaimPort;
import com.nursena.payflow.outbox.application.port.out.OutboxEventLifecyclePort;
import com.nursena.payflow.outbox.application.port.out.OutboxMessagePublisherPort;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import com.nursena.payflow.outbox.domain.model.OutboxStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;


@ExtendWith(MockitoExtension.class)
class PublishOutboxEventsServiceTest {

    private static final UUID FIRST_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000001"
        );

    private static final UUID SECOND_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000002"
        );

    private static final UUID THIRD_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000003"
        );

    private static final UUID FOURTH_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000004"
        );

    private static final UUID FIFTH_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000005"
        );

    private static final UUID AGGREGATE_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000000001"
        );

    private static final Instant CLOCK_TIME =
        Instant.parse(
            "2026-07-18T13:00:00.123456789Z"
        );

    private static final Instant NOW =
        Instant.parse(
            "2026-07-18T13:00:00.123456Z"
        );

    private static final Duration LEASE_DURATION =
        Duration.ofSeconds(30);

    private static final String PUBLISHER_ID =
        "publisher-1";

    private static final String EVENT_TYPE =
        "wallet.transfer.completed";

    private static final PublishOutboxEventsCommand
        COMMAND =
        new PublishOutboxEventsCommand(
            PUBLISHER_ID,
            10,
            LEASE_DURATION
        );

    @Mock
    private OutboxEventClaimPort claimPort;

    @Mock
    private OutboxMessagePublisherPort publisherPort;

    @Mock
    private OutboxEventLifecyclePort lifecyclePort;

    private PublishOutboxEventsService service;

    @BeforeEach
    void setUp() {
        OutboxRetryPolicy retryPolicy =
            new OutboxRetryPolicy(
                3,
                Duration.ofSeconds(10),
                Duration.ofSeconds(40)
            );

        service =
            new PublishOutboxEventsService(
                claimPort,
                publisherPort,
                lifecyclePort,
                retryPolicy,
                Clock.fixed(
                    CLOCK_TIME,
                    ZoneOffset.UTC
                )
            );
    }

    @Test
    void shouldReturnEmptyResultWhenNothingIsClaimed() {
        when(claimPort.claimAvailable(
            PUBLISHER_ID,
            NOW,
            LEASE_DURATION,
            10
        )).thenReturn(List.of());

        PublishOutboxEventsResult result =
            service.publishAvailable(COMMAND);

        assertThat(result)
            .isEqualTo(
                PublishOutboxEventsResult.empty()
            );

        verifyNoInteractions(
            publisherPort,
            lifecyclePort
        );
    }

    @Test
    void shouldPublishAndCompleteAllClaimedEvents() {
        OutboxEvent first =
            processingEvent(
                FIRST_EVENT_ID,
                1
            );

        OutboxEvent second =
            processingEvent(
                SECOND_EVENT_ID,
                1
            );

        when(claimPort.claimAvailable(
            PUBLISHER_ID,
            NOW,
            LEASE_DURATION,
            10
        )).thenReturn(
            List.of(first, second)
        );

        PublishOutboxEventsResult result =
            service.publishAvailable(COMMAND);

        assertThat(result)
            .isEqualTo(
                new PublishOutboxEventsResult(
                    2,
                    2,
                    0,
                    0,
                    0
                )
            );

        verify(publisherPort)
            .publish(first);

        verify(publisherPort)
            .publish(second);

        verify(lifecyclePort)
            .markPublished(
                FIRST_EVENT_ID,
                PUBLISHER_ID,
                NOW
            );

        verify(lifecyclePort)
            .markPublished(
                SECOND_EVENT_ID,
                PUBLISHER_ID,
                NOW
            );
    }

    @Test
    void shouldIsolateMixedBatchOutcomes() {
        OutboxEvent retryEvent =
            processingEvent(
                FIRST_EVENT_ID,
                1
            );

        OutboxEvent publishedEvent =
            processingEvent(
                SECOND_EVENT_ID,
                1
            );

        OutboxEvent failedEvent =
            processingEvent(
                THIRD_EVENT_ID,
                3
            );

        OutboxEvent unresolvedEvent =
            processingEvent(
                FOURTH_EVENT_ID,
                1
            );

        when(claimPort.claimAvailable(
            PUBLISHER_ID,
            NOW,
            LEASE_DURATION,
            10
        )).thenReturn(
            List.of(
                retryEvent,
                publishedEvent,
                failedEvent,
                unresolvedEvent
            )
        );

        doAnswer(invocation -> {
            OutboxEvent event =
                invocation.getArgument(0);

            if (FIRST_EVENT_ID.equals(event.id())) {
                throw new IllegalStateException(
                    "Broker is unavailable."
                );
            }

            if (THIRD_EVENT_ID.equals(event.id())) {
                throw new IllegalStateException(
                    "Invalid broker response."
                );
            }

            return null;
        })
            .when(publisherPort)
            .publish(
                any(OutboxEvent.class)
            );

        when(lifecyclePort.markPublished(
            any(UUID.class),
            anyString(),
            any(Instant.class)
        )).thenAnswer(invocation -> {
            UUID eventId =
                invocation.getArgument(0);

            String publisherId =
                invocation.getArgument(1);

            Instant publishedAt =
                invocation.getArgument(2);

            if (FOURTH_EVENT_ID.equals(eventId)) {
                throw new IllegalStateException(
                    "Database is unavailable."
                );
            }

            return processingEvent(
                eventId,
                1
            ).markPublished(
                publisherId,
                publishedAt
            );
        });

        PublishOutboxEventsResult result =
            service.publishAvailable(COMMAND);
        ArgumentCaptor<OutboxEvent>
            publishedEventCaptor =
            ArgumentCaptor.forClass(
                OutboxEvent.class
            );

        verify(
            publisherPort,
            times(4)
        ).publish(
            publishedEventCaptor.capture()
        );

        assertThat(
            publishedEventCaptor
                .getAllValues()
        )
            .extracting(
                OutboxEvent::id
            )
            .containsExactly(
                FIRST_EVENT_ID,
                SECOND_EVENT_ID,
                THIRD_EVENT_ID,
                FOURTH_EVENT_ID
            );

        assertThat(result)
            .isEqualTo(
                new PublishOutboxEventsResult(
                    4,
                    1,
                    1,
                    1,
                    1
                )
            );

        verify(lifecyclePort)
            .scheduleRetry(
                FIRST_EVENT_ID,
                PUBLISHER_ID,
                NOW,
                NOW.plusSeconds(10),
                "IllegalStateException: "
                    + "Broker is unavailable."
            );

        verify(lifecyclePort)
            .markPublished(
                SECOND_EVENT_ID,
                PUBLISHER_ID,
                NOW
            );

        verify(lifecyclePort)
            .markFailed(
                THIRD_EVENT_ID,
                PUBLISHER_ID,
                NOW,
                "IllegalStateException: "
                    + "Invalid broker response."
            );

        verify(publisherPort)
            .publish(unresolvedEvent);
    }

    @Test
    void shouldContinueWhenRetryOutcomeCannotBePersisted() {
        OutboxEvent unresolvedEvent =
            processingEvent(
                FOURTH_EVENT_ID,
                1
            );

        OutboxEvent successfulEvent =
            processingEvent(
                FIFTH_EVENT_ID,
                1
            );

        when(claimPort.claimAvailable(
            PUBLISHER_ID,
            NOW,
            LEASE_DURATION,
            10
        )).thenReturn(
            List.of(
                unresolvedEvent,
                successfulEvent
            )
        );

        doAnswer(invocation -> {
            OutboxEvent event =
                invocation.getArgument(0);

            if (FOURTH_EVENT_ID.equals(event.id())) {
                throw new IllegalStateException(
                    "Broker is unavailable."
                );
            }

            return null;
        })
            .when(publisherPort)
            .publish(
                any(OutboxEvent.class)
            );

        doThrow(
            new IllegalStateException(
                "Database is unavailable."
            )
        )
            .when(lifecyclePort)
            .scheduleRetry(
                FOURTH_EVENT_ID,
                PUBLISHER_ID,
                NOW,
                NOW.plusSeconds(10),
                "IllegalStateException: "
                    + "Broker is unavailable."
            );

        PublishOutboxEventsResult result =
            service.publishAvailable(COMMAND);

        assertThat(result)
            .isEqualTo(
                new PublishOutboxEventsResult(
                    2,
                    1,
                    0,
                    0,
                    1
                )
            );

        verify(publisherPort)
            .publish(successfulEvent);

        verify(lifecyclePort)
            .markPublished(
                FIFTH_EVENT_ID,
                PUBLISHER_ID,
                NOW
            );
    }

    private static OutboxEvent processingEvent(
        UUID eventId,
        int attemptCount
    ) {
        return OutboxEvent.rehydrate(
            eventId,
            "PAYMENT_TRANSACTION",
            AGGREGATE_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            AGGREGATE_ID.toString(),
            EVENT_TYPE + ":1:" + eventId,
            """
            {
              "eventId": "%s",
              "eventType": "wallet.transfer.completed",
              "eventVersion": 1
            }
            """.formatted(eventId),
            OutboxStatus.PROCESSING,
            attemptCount,
            NOW,
            NOW,
            NOW.plus(LEASE_DURATION),
            PUBLISHER_ID,
            NOW.minusSeconds(60),
            null,
            null
        );
    }
}
