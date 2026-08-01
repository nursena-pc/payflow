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
}