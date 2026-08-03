package com.nursena.payflow.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ObservabilityAcceptanceDocumentationTest {

    private static final Path README =
        Path.of("README.md");

    private static final Path OPERATIONS_GUIDE =
        Path.of(
            "docs",
            "operations",
            "structured-logging.md"
        );

    private static final Path RELEASE_NOTES =
        Path.of(
            "docs",
            "releases",
            "v0.11.0.md"
        );

    private static final Path POSTMAN_COLLECTION =
        Path.of(
            "postman",
            "PayFlow.postman_collection.json"
        );

    private static final Path DOCKER_SMOKE =
        Path.of(
            ".github",
            "workflows",
            "docker-smoke.yml"
        );

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    @Test
    void shouldDocumentSynchronousAndAsynchronousBoundaries()
        throws IOException {
        String guide =
            Files.readString(
                OPERATIONS_GUIDE
            );

        assertThat(guide)
            .contains(
                "## Synchronous and asynchronous boundaries"
            )
            .contains(
                "does not treat a request correlation ID as a business identifier"
            )
            .contains(
                "transactional outbox publication"
            )
            .contains(
                "Kafka consumers"
            )
            .contains(
                "must be an explicit event-schema decision"
            );
    }

    @Test
    void shouldDocumentBoundedExceptionPolicy()
        throws IOException {
        String guide =
            Files.readString(
                OPERATIONS_GUIDE
            );

        assertThat(guide)
            .contains(
                "## Exception and stack-trace policy"
            )
            .contains(
                "maximum depth per throwable: 30"
            )
            .contains(
                "maximum encoded exception length: 8192 characters"
            )
            .contains(
                "The request-completion event never includes a throwable"
            )
            .contains(
                "No profile enables request bodies"
            );
    }

    @Test
    void shouldPublishReleasePreparationNotesAndReadmeLink()
        throws IOException {
        String releaseNotes =
            Files.readString(
                RELEASE_NOTES
            );

        String readme =
            Files.readString(
                README
            );

        assertThat(releaseNotes)
            .contains(
                "# PayFlow v0.11.0"
            )
            .contains(
                "## Release assets"
            )
            .contains(
                "No database migration is included."
            )
            .contains(
                "Asynchronous request-correlation propagation remains deliberately out of scope"
            );

        assertThat(readme)
            .contains(
                "docs/releases/v0.11.0.md"
            )
            .contains(
                "PayFlow v0.11.0 is the latest published release"
            );
    }

    @Test
    void shouldVerifyCorrelationHeaderInPostman()
        throws IOException {
        JsonNode collection =
            objectMapper.readTree(
                Files.readString(
                    POSTMAN_COLLECTION
                )
            );

        String serialized =
            collection.toString();

        assertThat(serialized)
            .contains(
                "Response contains a bounded X-Correlation-ID"
            )
            .contains(
                "pm.response.headers.get('X-Correlation-ID')"
            )
            .contains(
                "to.be.at.most(64)"
            )
            .contains(
                "pm.environment.set('correlationId'"
            );
    }

    @Test
    void shouldDefineProtectedDockerSmokeContract()
        throws IOException {
        String workflow =
            Files.readString(
                DOCKER_SMOKE
            );

        assertThat(workflow)
            .contains(
                "name: Docker Smoke"
            )
            .contains(
                "pull_request:"
            )
            .contains(
                "SPRING_PROFILES_ACTIVE: production"
            )
            .contains(
                "docker compose config --quiet"
            )
            .contains(
                "GRAFANA_ADMIN_PASSWORD: payflow-smoke-local-only"
            )
            .contains(
                "http://localhost:8080/api/v1/system/health"
            )
            .contains(
                "X-Correlation-ID"
            )
            .contains(
                "\"event\":\"http.request.completed\""
            )
            .contains(
                "down -v --remove-orphans"
            );
    }
}
