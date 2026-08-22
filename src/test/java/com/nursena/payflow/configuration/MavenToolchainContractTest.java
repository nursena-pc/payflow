package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MavenToolchainContractTest {

    private static final Path WRAPPER_PROPERTIES =
        Path.of(
            ".mvn",
            "wrapper",
            "maven-wrapper.properties"
        );

    private static final Path CI_WORKFLOW =
        Path.of(
            ".github",
            "workflows",
            "ci.yml"
        );

    private static final Path RELEASE_WORKFLOW =
        Path.of(
            ".github",
            "workflows",
            "release.yml"
        );

    private static final Path DOCKERFILE =
        Path.of("Dockerfile");

    @Test
    void shouldPinReviewedMavenToolchainAcrossBuildPaths()
        throws IOException {

        assertThat(
            Files.readString(WRAPPER_PROPERTIES)
        )
            .contains(
                "distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip",
                "distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce"
            )
            .doesNotContain(
                "apache-maven/3.9.9/"
            );

        assertThat(
            Files.readString(CI_WORKFLOW)
        )
            .contains(
                "run: ./mvnw -B -ntp clean verify"
            )
            .doesNotContain(
                "run: mvn -B -ntp clean verify"
            );

        assertThat(
            Files.readString(RELEASE_WORKFLOW)
        )
            .contains(
                "VERSION=\"$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)\"",
                "run: ./mvnw -B -ntp clean verify"
            )
            .doesNotContain(
                "VERSION=\"$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)\"",
                "run: mvn -B -ntp clean verify"
            );

        assertThat(
            Files.readString(DOCKERFILE)
        )
            .startsWith(
                "FROM maven:3.9.16-eclipse-temurin-21-noble@sha256:613124833fa6718ded9d655a2ebfab6425818c178f899116b93560b6f1c9ffe9 AS build"
            );
    }
}
