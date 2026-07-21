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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Qualifier;

@SpringBootTest
@Testcontainers
@Import(
    ProcessTransferCompletedEventTransactionIntegrationTest
        .ProcessingTestConfiguration.class
)
class ProcessTransferCompletedEventTransactionIntegrationTest {

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000701"
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
    private TestTransferCompletedEventHandler
        eventHandler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM processed_kafka_events"
        );

        eventHandler.reset();
    }

    @Test
    void shouldRollbackProcessedEventWhenHandlerFails() {
        eventHandler.fail();

        assertThatThrownBy(
            () -> useCase.process(command())
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "test handler failure"
            );

        assertThat(processedEventCount())
            .isZero();

        eventHandler.succeed();

        ProcessTransferCompletedEventResult retryResult =
            useCase.process(command());

        assertThat(retryResult)
            .isEqualTo(
                ProcessTransferCompletedEventResult
                    .PROCESSED
            );

        assertThat(processedEventCount())
            .isEqualTo(1);

        ProcessTransferCompletedEventResult duplicateResult =
            useCase.process(command());

        assertThat(duplicateResult)
            .isEqualTo(
                ProcessTransferCompletedEventResult
                    .DUPLICATE
            );

        assertThat(processedEventCount())
            .isEqualTo(1);

        assertThat(eventHandler.invocationCount())
            .isEqualTo(2);
    }

    private Integer processedEventCount() {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM processed_kafka_events
            WHERE consumer_name = ?
              AND event_id = ?
            """,
            Integer.class,
            "transfer-completed-notification",
            EVENT_ID
        );
    }

    private static
    ProcessTransferCompletedEventCommand command() {
        return new ProcessTransferCompletedEventCommand(
            event(),
            TransferCompletedEvent.TYPE,
            0,
            25L
        );
    }

    private static TransferCompletedEvent event() {
        return new TransferCompletedEvent(
            EVENT_ID,
            TransferCompletedEvent.TYPE,
            TransferCompletedEvent.VERSION,
            Instant.parse(
                "2026-07-20T20:00:00Z"
            ),
            UUID.fromString(
                "60000000-0000-0000-0000-000000000701"
            ),
            UUID.fromString(
                "70000000-0000-0000-0000-000000000701"
            ),
            UUID.fromString(
                "70000000-0000-0000-0000-000000000702"
            ),
            "125.50",
            "TRY"
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProcessingTestConfiguration {

        @Bean
        TestTransferCompletedEventHandler
        testTransferCompletedEventHandler() {
            return new TestTransferCompletedEventHandler();
        }

        @Bean
        ProcessTransferCompletedEventUseCase
        processTransferCompletedEventUseCase(
            ProcessedKafkaEventRepositoryPort repository,
            TestTransferCompletedEventHandler handler
        ) {
            return new ProcessTransferCompletedEventService(
                "transfer-completed-notification",
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

    static final class
    TestTransferCompletedEventHandler
        implements TransferCompletedEventHandlerPort {

        private boolean failing;

        private int invocationCount;

        @Override
        public void handle(
            TransferCompletedEvent event
        ) {
            invocationCount++;

            if (failing) {
                throw new IllegalStateException(
                    "test handler failure"
                );
            }
        }

        void fail() {
            failing = true;
        }

        void succeed() {
            failing = false;
        }

        void reset() {
            failing = false;
            invocationCount = 0;
        }

        int invocationCount() {
            return invocationCount;
        }
    }
}
