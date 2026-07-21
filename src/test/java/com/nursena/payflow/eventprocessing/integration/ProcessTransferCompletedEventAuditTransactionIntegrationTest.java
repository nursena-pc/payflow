package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.ProcessTransferCompletedEventCommand;
import com.nursena.payflow.eventprocessing.application.model.ProcessTransferCompletedEventResult;
import com.nursena.payflow.eventprocessing.application.port.in.ProcessTransferCompletedEventUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.ProcessedKafkaEventRepositoryPort;
import com.nursena.payflow.eventprocessing.application.port.out.TransferCompletedEventHandlerPort;
import com.nursena.payflow.eventprocessing.application.service.ProcessTransferCompletedEventService;
import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Qualifier;

@SpringBootTest
@Testcontainers
@Import(
    ProcessTransferCompletedEventAuditTransactionIntegrationTest
        .ProcessingTestConfiguration.class
)
class ProcessTransferCompletedEventAuditTransactionIntegrationTest {

    private static final String CONSUMER_NAME =
        "transfer-completed-audit";

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000001001"
        );

    private static final UUID SECOND_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000001002"
        );

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000001001"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    @Qualifier("processTransferCompletedEventUseCase")
    private ProcessTransferCompletedEventUseCase
        useCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM transfer_completed_event_audits"
        );

        jdbcTemplate.update(
            "DELETE FROM processed_kafka_events"
        );
    }

    @Test
    void shouldPersistProcessedMarkerAndAuditOnce() {
        ProcessTransferCompletedEventResult firstResult =
            useCase.process(
                command(
                    EVENT_ID,
                    25L
                )
            );

        assertThat(firstResult)
            .isEqualTo(
                ProcessTransferCompletedEventResult
                    .PROCESSED
            );

        assertThat(
            processedEventCount(EVENT_ID)
        )
            .isEqualTo(1);

        assertThat(
            auditCount(EVENT_ID)
        )
            .isEqualTo(1);

        ProcessTransferCompletedEventResult duplicateResult =
            useCase.process(
                command(
                    EVENT_ID,
                    25L
                )
            );

        assertThat(duplicateResult)
            .isEqualTo(
                ProcessTransferCompletedEventResult
                    .DUPLICATE
            );

        assertThat(
            processedEventCount(EVENT_ID)
        )
            .isEqualTo(1);

        assertThat(
            auditCount(EVENT_ID)
        )
            .isEqualTo(1);
    }

    @Test
    void shouldRollbackProcessedMarkerWhenAuditInsertFails() {
        ProcessTransferCompletedEventResult firstResult =
            useCase.process(
                command(
                    EVENT_ID,
                    25L
                )
            );

        assertThat(firstResult)
            .isEqualTo(
                ProcessTransferCompletedEventResult
                    .PROCESSED
            );

        assertThatThrownBy(
            () -> useCase.process(
                command(
                    SECOND_EVENT_ID,
                    26L
                )
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );

        assertThat(
            processedEventCount(EVENT_ID)
        )
            .isEqualTo(1);

        assertThat(
            processedEventCount(SECOND_EVENT_ID)
        )
            .isZero();

        assertThat(
            auditCountByTransactionId(
                TRANSACTION_ID
            )
        )
            .isEqualTo(1);
    }

    private Integer processedEventCount(
        UUID eventId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM processed_kafka_events
            WHERE consumer_name = ?
              AND event_id = ?
            """,
            Integer.class,
            CONSUMER_NAME,
            eventId
        );
    }

    private Integer auditCount(
        UUID eventId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM transfer_completed_event_audits
            WHERE event_id = ?
            """,
            Integer.class,
            eventId
        );
    }

    private Integer auditCountByTransactionId(
        UUID transactionId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM transfer_completed_event_audits
            WHERE transaction_id = ?
            """,
            Integer.class,
            transactionId
        );
    }

    private static
    ProcessTransferCompletedEventCommand command(
        UUID eventId,
        long recordOffset
    ) {
        return new ProcessTransferCompletedEventCommand(
            event(eventId),
            TransferCompletedEvent.TYPE,
            0,
            recordOffset
        );
    }

    private static TransferCompletedEvent event(
        UUID eventId
    ) {
        return new TransferCompletedEvent(
            eventId,
            TransferCompletedEvent.TYPE,
            TransferCompletedEvent.VERSION,
            Instant.parse(
                "2026-07-20T20:00:00Z"
            ),
            TRANSACTION_ID,
            UUID.fromString(
                "70000000-0000-0000-0000-000000001001"
            ),
            UUID.fromString(
                "70000000-0000-0000-0000-000000001002"
            ),
            "125.50",
            "TRY"
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProcessingTestConfiguration {

        @Bean
        ProcessTransferCompletedEventUseCase
        processTransferCompletedEventUseCase(
            ProcessedKafkaEventRepositoryPort repository,
            TransferCompletedEventHandlerPort handler
        ) {
            return new ProcessTransferCompletedEventService(
                CONSUMER_NAME,
                repository,
                handler,
                Clock.fixed(
                    Instant.parse(
                        "2026-07-20T21:00:00Z"
                    ),
                    ZoneOffset.UTC
                )
            );
        }
    }
}
