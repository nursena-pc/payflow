package com.nursena.payflow.maildelivery.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.maildelivery.application.policy.MailRetryPolicy;
import com.nursena.payflow.maildelivery.application.port.in.DispatchMailOutboxCommand;
import com.nursena.payflow.maildelivery.application.port.in.DispatchMailOutboxResult;
import com.nursena.payflow.maildelivery.application.port.out.MailDeliveryPort;
import com.nursena.payflow.maildelivery.application.port.out.MailOutboxClaimPort;
import com.nursena.payflow.maildelivery.application.port.out.MailOutboxLifecyclePort;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxPurpose;
import com.nursena.payflow.maildelivery.domain.model.ProtectedMailContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchMailOutboxServiceTest {

    private static final Instant NOW =
        Instant.parse("2026-08-05T18:00:00Z");
    private static final UUID MESSAGE_ID = UUID.fromString(
        "72463bbc-1ce1-4960-b092-1a9564388f2e"
    );

    @Mock
    private MailOutboxClaimPort claimPort;
    @Mock
    private MailDeliveryPort deliveryPort;
    @Mock
    private MailOutboxLifecyclePort lifecyclePort;

    private DispatchMailOutboxService service;
    private DispatchMailOutboxCommand command;

    @BeforeEach
    void setUp() {
        service = new DispatchMailOutboxService(
            claimPort,
            deliveryPort,
            lifecyclePort,
            new MailRetryPolicy(
                5,
                Duration.ofSeconds(30),
                Duration.ofMinutes(10)
            ),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        command = new DispatchMailOutboxCommand(
            "mail-worker",
            10,
            Duration.ofSeconds(30)
        );
    }

    @Test
    void shouldDeliverAndPersistSentOutcome() {
        MailOutboxMessage message = claimedMessage();
        when(claimPort.claimAvailable(
            "mail-worker",
            NOW,
            Duration.ofSeconds(30),
            10
        )).thenReturn(List.of(message));

        DispatchMailOutboxResult result = service.dispatch(command);

        assertThat(result.sentCount()).isEqualTo(1);
        verify(deliveryPort).send(message);
        verify(lifecyclePort).markSent(
            MESSAGE_ID,
            "mail-worker",
            NOW
        );
    }

    @Test
    void shouldRetryWithoutPersistingExceptionDetails() {
        MailOutboxMessage message = claimedMessage();
        when(claimPort.claimAvailable(
            "mail-worker",
            NOW,
            Duration.ofSeconds(30),
            10
        )).thenReturn(List.of(message));
        doThrow(new IllegalStateException(
            "contains-secret-token-and-recipient"
        )).when(deliveryPort).send(message);

        DispatchMailOutboxResult result = service.dispatch(command);

        assertThat(result.retriedCount()).isEqualTo(1);
        verify(lifecyclePort).scheduleRetry(
            MESSAGE_ID,
            "mail-worker",
            NOW,
            NOW.plusSeconds(30),
            "IllegalStateException"
        );
    }

    private static MailOutboxMessage claimedMessage() {
        return MailOutboxMessage.pending(
            MESSAGE_ID,
            UUID.randomUUID(),
            MailOutboxPurpose.EMAIL_VERIFICATION,
            "nursena@example.com",
            "Verify your PayFlow email",
            ProtectedMailContent.of(new byte[]{1, 2, 3}),
            "<account-action-" + MESSAGE_ID + "@payflow.local>",
            NOW.minusSeconds(1),
            NOW.plus(Duration.ofHours(1))
        ).claim(
            "mail-worker",
            NOW,
            Duration.ofSeconds(30)
        );
    }
}
