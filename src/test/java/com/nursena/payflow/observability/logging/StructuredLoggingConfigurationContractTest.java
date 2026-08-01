package com.nursena.payflow.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class StructuredLoggingConfigurationContractTest {

    private static final Path POM =
        Path.of("pom.xml");

    private static final Path LOGBACK =
        Path.of(
            "src",
            "main",
            "resources",
            "logback-spring.xml"
        );

    private static final Path README =
        Path.of("README.md");

    private static final Path OPERATIONS_GUIDE =
        Path.of(
            "docs",
            "operations",
            "structured-logging.md"
        );

    @Test
    void shouldPinJackson2CompatibleEncoderRelease()
        throws IOException {
        String pom =
            Files.readString(POM);

        assertThat(pom)
            .contains(
                "<logstash-logback-encoder.version>"
                    + "8.1"
                    + "</logstash-logback-encoder.version>"
            )
            .contains(
                "<artifactId>"
                    + "logstash-logback-encoder"
                    + "</artifactId>"
            )
            .doesNotContain(
                "<logstash-logback-encoder.version>"
                    + "9.0"
            );
    }

    @Test
    void shouldDefineProductionJsonProfile()
        throws IOException {
        String configuration =
            Files.readString(LOGBACK);

        assertThat(configuration)
            .contains(
                "structured-logging | production"
            )
            .contains(
                "net.logstash.logback.encoder.LogstashEncoder"
            )
            .contains(
                "\"schemaVersion\":1"
            )
            .contains(
                "<lineSeparator>UNIX</lineSeparator>"
            );
    }

    @Test
    void shouldWhitelistCorrelationContextAndDisableArguments()
        throws IOException {
        String configuration =
            Files.readString(LOGBACK);

        assertThat(configuration)
            .contains(
                "<includeMdcKeyName>"
                    + "correlationId"
                    + "</includeMdcKeyName>"
            )
            .contains(
                "<includeKeyValuePairs>"
                    + "false"
                    + "</includeKeyValuePairs>"
            )
            .contains(
                "<includeStructuredArguments>"
                    + "false"
                    + "</includeStructuredArguments>"
            )
            .contains(
                "<includeNonStructuredArguments>"
                    + "false"
                    + "</includeNonStructuredArguments>"
            );
    }

    @Test
    void shouldConfigureFieldAndValueRedaction()
        throws IOException {
        String configuration =
            Files.readString(LOGBACK);

        assertThat(configuration)
            .contains(
                "MaskingJsonGeneratorDecorator"
            )
            .contains(
                "<defaultMask>[REDACTED]</defaultMask>"
            )
            .contains(
                "password,"
            )
            .contains(
                "authorization,"
            )
            .contains(
                "refreshToken,"
            )
            .contains(
                "accessToken,"
            )
            .contains(
                SensitiveLogValueMasker.class
                    .getName()
            );
    }

    @Test
    void shouldKeepReadableLocalPatternWithCorrelationId()
        throws IOException {
        String configuration =
            Files.readString(LOGBACK);

        assertThat(configuration)
            .contains(
                "!structured-logging &amp; !production"
            )
            .contains(
                "correlationId=%X{correlationId:-none}"
            )
            .contains(
                "PLAIN_CONSOLE"
            );
    }

    @Test
    void shouldWhitelistOnlyBoundedRequestCompletionFields()
        throws IOException {
        String configuration =
            Files.readString(LOGBACK);

        assertThat(configuration)
            .contains(
                mdcAllowlistEntry(
                    Slf4jRequestCompletionLogger.EVENT_KEY
                )
            )
            .contains(
                mdcAllowlistEntry(
                    Slf4jRequestCompletionLogger.METHOD_KEY
                )
            )
            .contains(
                mdcAllowlistEntry(
                    Slf4jRequestCompletionLogger.ROUTE_KEY
                )
            )
            .contains(
                mdcAllowlistEntry(
                    Slf4jRequestCompletionLogger.STATUS_CODE_KEY
                )
            )
            .contains(
                mdcAllowlistEntry(
                    Slf4jRequestCompletionLogger.DURATION_KEY
                )
            )
            .contains(
                mdcAllowlistEntry(
                    Slf4jRequestCompletionLogger.OUTCOME_KEY
                )
            )
            .doesNotContain(
                "<includeMdcKeyName>authorization</includeMdcKeyName>",
                "<includeMdcKeyName>cookie</includeMdcKeyName>",
                "<includeMdcKeyName>query</includeMdcKeyName>",
                "<includeMdcKeyName>request.body</includeMdcKeyName>",
                "<includeMdcKeyName>response.body</includeMdcKeyName>"
            );
    }

    @Test
    void shouldDocumentActivationFieldsAndSecurityBoundaries()
        throws IOException {
        String readme =
            Files.readString(README);

        String operationsGuide =
            Files.readString(
                OPERATIONS_GUIDE
            );

        assertThat(readme)
            .contains(
                "docs/operations/structured-logging.md"
            );

        assertThat(operationsGuide)
            .contains(
                "SPRING_PROFILES_ACTIVE"
            )
            .contains(
                "`http.route`"
            )
            .contains(
                "`UNMATCHED`"
            )
            .contains(
                "Query strings are never logged."
            )
            .contains(
                "Request and response bodies are never logged."
            )
            .contains(
                "Authorization and cookie headers are never logged."
            );
    }

    private static String mdcAllowlistEntry(
        String key
    ) {
        return "<includeMdcKeyName>"
            + key
            + "</includeMdcKeyName>";
    }
}