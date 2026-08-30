package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V100RecoveryMigrationApiDocumentationFreezeContractTest {

    private static final Path POM =
        Path.of("pom.xml");

    private static final Path OPENAPI_CONFIGURATION =
        Path.of(
            "src",
            "main",
            "java",
            "com",
            "nursena",
            "payflow",
            "configuration",
            "OpenApiConfiguration.java"
        );

    private static final Path OPENAPI_UNIT_TEST =
        Path.of(
            "src",
            "test",
            "java",
            "com",
            "nursena",
            "payflow",
            "configuration",
            "OpenApiConfigurationTest.java"
        );

    private static final Path OPENAPI_INTEGRATION_TEST =
        Path.of(
            "src",
            "test",
            "java",
            "com",
            "nursena",
            "payflow",
            "configuration",
            "integration",
            "OpenApiJsonContractIntegrationTest.java"
        );

    private static final Path API_BASELINE =
        Path.of(
            "docs",
            "api-v1-compatibility.md"
        );

    private static final Path FLYWAY_RUNBOOK =
        Path.of(
            "docs",
            "operations",
            "flyway-clean-upgrade.md"
        );

    private static final Path POSTGRES_RUNBOOK =
        Path.of(
            "docs",
            "operations",
            "postgresql-backup-restore.md"
        );

    private static final Path REDIS_KAFKA_RUNBOOK =
        Path.of(
            "docs",
            "operations",
            "redis-kafka-outage-recovery.md"
        );

    @Test
    void shouldAlignOpenApiMetadataWithExactV1ReleasePreparation()
        throws IOException {

        String pom = Files.readString(POM);
        String openApi =
            Files.readString(OPENAPI_CONFIGURATION);
        String unitTest =
            Files.readString(OPENAPI_UNIT_TEST);
        String integrationTest =
            Files.readString(OPENAPI_INTEGRATION_TEST);

        assertThat(pom)
            .contains(
                "<version>1.0.0</version>"
            )
            .doesNotContain(
                "<version>1.0.0-SNAPSHOT</version>"
            );

        assertThat(openApi)
            .contains(
                "API_VERSION",
                "\"1.0.0\""
            )
            .doesNotContain(
                "\"0.16.0\"",
                "\"0.2.0\""
            );

        assertThat(unitTest)
            .contains(
                ".isEqualTo(\"1.0.0\");"
            );

        assertThat(integrationTest)
            .contains(
                ".isEqualTo(\"1.0.0\");"
            );
    }

    @Test
    void shouldCarryHistoricalApiBaselineIntoV1Freeze()
        throws IOException {

        String baseline =
            Files.readString(API_BASELINE);

        assertThat(baseline)
            .contains(
                "Current v1.0.0 CP5 review",
                "**30 canonical HTTP operations**",
                "**28 unique route paths**",
                "v0.16.0",
                "compatibility baseline",
                "During the v1.0.0 CP5 review",
                "`1.0.0-SNAPSHOT`",
                "On the current release-preparation candidate",
                "both exact `1.0.0`"
            )
            .contains(
                "carried unchanged into the v1.0.0 "
                    + "release-candidate"
            );
    }

    @Test
    void shouldAlignRecoveryRunbooksWithV1Candidate()
        throws IOException {

        String flyway =
            Files.readString(FLYWAY_RUNBOOK);
        String postgres =
            Files.readString(POSTGRES_RUNBOOK);
        String redisKafka =
            Files.readString(REDIS_KAFKA_RUNBOOK);

        assertThat(flyway)
            .contains(
                "Current v1.0.0 CP5 review",
                "current `1.0.0` release-preparation application",
                "current v1.0.0 release-candidate line"
            )
            .doesNotContain(
                "current v0.16.0 line",
                "current `0.16.0-SNAPSHOT` application",
                "current v0.16.0 application"
            );

        assertThat(postgres)
            .contains(
                "Current v1.0.0 CP5 review",
                "target/<artifactId>-<project.version>.jar",
                "release-preparation candidate",
                "target/payflow-1.0.0.jar"
            )
            .doesNotContain(
                "target/payflow-0.16.0-SNAPSHOT.jar",
                "target/payflow-1.0.0-SNAPSHOT.jar"
            );

        assertThat(redisKafka)
            .contains(
                "Current v1.0.0 CP5 review",
                "PayFlow v1.0.0 release-candidate CP5",
                "V016RedisOutageRecoveryRehearsalTest",
                "historical introduction point"
            );
    }

    @Test
    void shouldRecordReviewedPostgresRecoveryEvidence()
        throws IOException {

        String postgres =
            Files.readString(POSTGRES_RUNBOOK);

        assertThat(postgres)
            .contains(
                "33f5ce5e37ec0e947ad21d7fd415409b8773063b",
                "30898695e9647c98a678551950131d6c22ec882d35d220ec19b606827b8fa0f7",
                "6757de9340dd02515ce11d2ec83685ea397c9f4a2a11341616008499cccad330",
                "source fingerprint unchanged",
                "HTTP `200`"
            )
            .contains(
                "production disaster-recovery",
                "RPO",
                "RTO certification"
            );
    }
}
