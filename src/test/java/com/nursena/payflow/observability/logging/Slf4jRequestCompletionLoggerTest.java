package com.nursena.payflow.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class Slf4jRequestCompletionLoggerTest {

    private Logger logger;
    private Level originalLevel;
    private boolean originalAdditive;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger =
            (Logger) LoggerFactory.getLogger(
                Slf4jRequestCompletionLogger.class
            );

        originalLevel =
            logger.getLevel();

        originalAdditive =
            logger.isAdditive();

        logger.setLevel(
            Level.INFO
        );

        logger.setAdditive(
            false
        );

        appender =
            new SnapshottingListAppender();

        appender.setContext(
            logger.getLoggerContext()
        );

        appender.start();

        logger.addAppender(
            appender
        );
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(
            appender
        );

        appender.stop();

        logger.setLevel(
            originalLevel
        );

        logger.setAdditive(
            originalAdditive
        );

        MDC.clear();
    }

    @Test
    void shouldWriteSafeStructuredFieldsAndPreserveCorrelation() {
        MDC.put(
            "correlationId",
            "request-123"
        );

        new Slf4jRequestCompletionLogger()
            .completed(
                completion()
            );

        assertThat(
            appender.list
        )
            .hasSize(1);

        ILoggingEvent event =
            appender.list.get(0);

        assertThat(
            event.getMDCPropertyMap()
        )
            .containsEntry(
                "correlationId",
                "request-123"
            )
            .containsEntry(
                Slf4jRequestCompletionLogger.EVENT_KEY,
                Slf4jRequestCompletionLogger
                    .COMPLETION_EVENT
            )
            .containsEntry(
                Slf4jRequestCompletionLogger.METHOD_KEY,
                "GET"
            )
            .containsEntry(
                Slf4jRequestCompletionLogger.ROUTE_KEY,
                "/api/v1/wallets/{walletId}"
            )
            .containsEntry(
                Slf4jRequestCompletionLogger.STATUS_CODE_KEY,
                "200"
            )
            .containsEntry(
                Slf4jRequestCompletionLogger.DURATION_KEY,
                "12"
            )
            .containsEntry(
                Slf4jRequestCompletionLogger.OUTCOME_KEY,
                "SUCCESS"
            );

        assertThat(
            MDC.get("correlationId")
        )
            .isEqualTo(
                "request-123"
            );

        assertTemporaryFieldsCleared();
    }

    @Test
    void shouldRestorePreexistingTemporaryFields() {
        MDC.put(
            Slf4jRequestCompletionLogger.EVENT_KEY,
            "previous-event"
        );

        MDC.put(
            Slf4jRequestCompletionLogger.METHOD_KEY,
            "PREVIOUS"
        );

        new Slf4jRequestCompletionLogger()
            .completed(
                completion()
            );

        assertThat(
            MDC.get(
                Slf4jRequestCompletionLogger.EVENT_KEY
            )
        )
            .isEqualTo(
                "previous-event"
            );

        assertThat(
            MDC.get(
                Slf4jRequestCompletionLogger.METHOD_KEY
            )
        )
            .isEqualTo(
                "PREVIOUS"
            );

        assertThat(
            MDC.get(
                Slf4jRequestCompletionLogger.ROUTE_KEY
            )
        )
            .isNull();
    }

    @Test
    void shouldExposeOnlyBoundedCompletionFields() {
        new Slf4jRequestCompletionLogger()
            .completed(
                completion()
            );

        Map<String, String> fields =
            appender.list
                .get(0)
                .getMDCPropertyMap();

        assertThat(fields)
            .containsOnlyKeys(
                Slf4jRequestCompletionLogger.EVENT_KEY,
                Slf4jRequestCompletionLogger.METHOD_KEY,
                Slf4jRequestCompletionLogger.ROUTE_KEY,
                Slf4jRequestCompletionLogger.STATUS_CODE_KEY,
                Slf4jRequestCompletionLogger.DURATION_KEY,
                Slf4jRequestCompletionLogger.OUTCOME_KEY
            );

        assertThat(
            appender.list
                .get(0)
                .getFormattedMessage()
        )
            .doesNotContain(
                "password",
                "authorization",
                "token",
                "secret"
            );

        assertTemporaryFieldsCleared();
    }

    private static HttpRequestCompletion completion() {
        return new HttpRequestCompletion(
            "GET",
            "/api/v1/wallets/{walletId}",
            200,
            12,
            HttpRequestOutcome.SUCCESS
        );
    }

    private static void assertTemporaryFieldsCleared() {
        assertThat(
            MDC.get(
                Slf4jRequestCompletionLogger.EVENT_KEY
            )
        )
            .isNull();

        assertThat(
            MDC.get(
                Slf4jRequestCompletionLogger.METHOD_KEY
            )
        )
            .isNull();

        assertThat(
            MDC.get(
                Slf4jRequestCompletionLogger.ROUTE_KEY
            )
        )
            .isNull();

        assertThat(
            MDC.get(
                Slf4jRequestCompletionLogger.STATUS_CODE_KEY
            )
        )
            .isNull();

        assertThat(
            MDC.get(
                Slf4jRequestCompletionLogger.DURATION_KEY
            )
        )
            .isNull();

        assertThat(
            MDC.get(
                Slf4jRequestCompletionLogger.OUTCOME_KEY
            )
        )
            .isNull();
    }

    private static final class SnapshottingListAppender
        extends ListAppender<ILoggingEvent> {

        @Override
        protected void append(
            ILoggingEvent event
        ) {
            event.prepareForDeferredProcessing();

            super.append(
                event
            );
        }
    }
}