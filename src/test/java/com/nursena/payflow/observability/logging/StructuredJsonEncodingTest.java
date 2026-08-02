package com.nursena.payflow.observability.logging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;

import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import net.logstash.logback.mask.MaskingJsonGeneratorDecorator;

import org.junit.jupiter.api.Test;

class StructuredJsonEncodingTest {

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    @Test
    void shouldEncodeOneValidJsonLineWithStableFields()
        throws Exception {
        LogstashEncoder encoder =
            encoder(true);

        try {
            LoggingEvent event =
                event(
                    "Transfer completed.",
                    Map.of(
                        "correlationId",
                        "request-123",
                        "unrelated",
                        "must-not-appear"
                    )
                );

            String encoded =
                new String(
                    encoder.encode(event),
                    UTF_8
                );

            JsonNode json =
                objectMapper.readTree(encoded);

            assertThat(
                json.path("service").asText()
            )
                .isEqualTo(
                    "payflow"
                );

            assertThat(
                json.path("schemaVersion").asInt()
            )
                .isEqualTo(1);

            assertThat(
                json.path("message").asText()
            )
                .isEqualTo(
                    "Transfer completed."
                );

            assertThat(
                json.path("correlationId").asText()
            )
                .isEqualTo(
                    "request-123"
                );

            assertThat(
                json.has("unrelated")
            )
                .isFalse();

            assertThat(
                encoded.indexOf('\n')
            )
                .isEqualTo(
                    encoded.length() - 1
                );

            assertThat(encoded)
                .doesNotContain("\r");
        }
        finally {
            encoder.stop();
        }
    }

    @Test
    void shouldRedactSensitiveMessageValues()
        throws Exception {
        LogstashEncoder encoder =
            encoder(true);

        try {
            String jwt =
                "eyJhbGciOiJSUzI1NiJ9"
                    + ".eyJzdWIiOiIxMjM0NTY3ODkwIn0"
                    + ".signature-value";

            LoggingEvent event =
                event(
                    "Authorization: Bearer "
                        + jwt
                        + "\r\npassword=secret-value",
                    Map.of(
                        "correlationId",
                        "request-456"
                    )
                );

            String encoded =
                new String(
                    encoder.encode(event),
                    UTF_8
                );

            JsonNode json =
                objectMapper.readTree(encoded);

            assertThat(
                json.path("message").asText()
            )
                .contains(
                    "[REDACTED]"
                )
                .doesNotContain(
                    jwt,
                    "secret-value"
                );

            assertThat(encoded)
                .doesNotContain(
                    jwt,
                    "secret-value"
                );

            assertThat(
                encoded.indexOf('\n')
            )
                .isEqualTo(
                    encoded.length() - 1
                );
        }
        finally {
            encoder.stop();
        }
    }

    @Test
    void shouldMaskSensitiveStructuredFieldsByPath()
        throws Exception {
        LogstashEncoder encoder =
            encoder(false);

        try {
            Map<String, String> mdc =
                new HashMap<>();

            mdc.put(
                "correlationId",
                "request-789"
            );

            mdc.put(
                "password",
                "structured-secret"
            );

            LoggingEvent event =
                event(
                    "Structured field test.",
                    mdc
                );

            JsonNode json =
                objectMapper.readTree(
                    encoder.encode(event)
                );

            assertThat(
                json.path("password").asText()
            )
                .isEqualTo(
                    SensitiveLogValueMasker.MASK
                );

            assertThat(
                json.toString()
            )
                .doesNotContain(
                    "structured-secret"
                );
        }
        finally {
            encoder.stop();
        }
    }

    private static LogstashEncoder encoder(
        boolean correlationOnly
    ) {
        LoggerContext context =
            new LoggerContext();

        context.start();

        LogstashEncoder encoder =
            new LogstashEncoder();

        encoder.setContext(context);
        encoder.setCustomFields(
            """
            {
              "service": "payflow",
              "schemaVersion": 1
            }
            """
        );

        encoder.setIncludeMdc(true);
        encoder.setIncludeContext(false);
        encoder.setIncludeKeyValuePairs(false);
        encoder.setIncludeStructuredArguments(false);
        encoder.setIncludeNonStructuredArguments(false);
        encoder.setLineSeparator("UNIX");

        if (correlationOnly) {
            encoder.addIncludeMdcKeyName(
                "correlationId"
            );
        }

        LogstashFieldNames fieldNames =
            new LogstashFieldNames();

        fieldNames.setTimestamp(
            "timestamp"
        );

        fieldNames.setVersion(null);
        fieldNames.setMessage(
            "message"
        );

        fieldNames.setLogger(
            "logger"
        );

        fieldNames.setThread(
            "thread"
        );

        fieldNames.setLevel(
            "level"
        );

        fieldNames.setLevelValue(null);
        fieldNames.setStackTrace(
            "exception"
        );

        fieldNames.setTags(null);

        encoder.setFieldNames(
            fieldNames
        );

        MaskingJsonGeneratorDecorator decorator =
            new MaskingJsonGeneratorDecorator();

        decorator.setDefaultMask(
            SensitiveLogValueMasker.MASK
        );

        decorator.addPaths(
            "password,passphrase,authorization,"
                + "refreshToken,refresh_token,"
                + "accessToken,access_token,"
                + "clientSecret,client_secret,"
                + "apiKey,api_key,"
                + "privateKey,private_key,"
                + "secret,token"
        );

        decorator.addValueMasker(
            new SensitiveLogValueMasker()
        );

        encoder.setJsonGeneratorDecorator(
            decorator
        );

        decorator.start();
        encoder.start();

        return encoder;
    }

    private static LoggingEvent event(
        String message,
        Map<String, String> mdc
    ) {
        LoggingEvent event =
            new LoggingEvent();

        event.setLoggerName(
            "com.nursena.payflow.TestLogger"
        );

        event.setLevel(
            Level.INFO
        );

        event.setThreadName(
            "test-thread"
        );

        event.setInstant(
            Instant.parse(
                "2026-01-01T00:00:00Z"
            )
        );

        event.setMessage(message);
        event.setMDCPropertyMap(
            new HashMap<>(mdc)
        );

        return event;
    }
}