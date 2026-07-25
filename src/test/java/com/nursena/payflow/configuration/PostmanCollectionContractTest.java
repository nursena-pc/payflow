package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PostmanCollectionContractTest {

    private static final Path COLLECTION_PATH =
        Path.of(
            "postman",
            "PayFlow.postman_collection.json"
        );

    private static final Path ENVIRONMENT_PATH =
        Path.of(
            "postman",
            "PayFlow.local.postman_environment.json"
        );

    private static final String LIST_REQUEST_NAME =
        "List Kafka Dead-Letter Command Audits";

    private static final String TIMELINE_REQUEST_NAME =
        "Get Kafka Dead-Letter Command Audit Timeline";

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    @Test
    void shouldExposeCommandAuditOperationsRequests()
        throws IOException {
        JsonNode collection =
            objectMapper.readTree(
                COLLECTION_PATH.toFile()
            );

        JsonNode operations =
            findNamedItem(
                collection.path("item"),
                "Operations"
            );

        JsonNode listRequest =
            findNamedItem(
                operations.path("item"),
                LIST_REQUEST_NAME
            );

        assertGetRequest(
            listRequest,
            "{{baseUrl}}/api/v1/operations/kafka/"
                + "dead-letter-command-audits"
                + "?page={{auditPage}}&size={{auditSize}}"
        );

        assertThat(
            queryVariables(listRequest)
        ).containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "page",
                "{{auditPage}}",
                "size",
                "{{auditSize}}",
                "commandId",
                "{{auditCommandId}}",
                "operatorId",
                "{{auditOperatorId}}",
                "deadLetterRecordId",
                "{{deadLetterRecordId}}",
                "commandType",
                "REPLAY",
                "stage",
                "COMPLETED",
                "outcome",
                "REPLAYED"
            )
        );

        String listPreRequest =
            eventScript(listRequest, "prerequest");

        assertThat(listPreRequest)
            .contains(
                "operatorAccessToken",
                "auditPage",
                "auditSize"
            );

        assertSafeAuditResponseAssertions(
            eventScript(listRequest, "test")
        );

        JsonNode timelineRequest =
            findNamedItem(
                operations.path("item"),
                TIMELINE_REQUEST_NAME
            );

        assertGetRequest(
            timelineRequest,
            "{{baseUrl}}/api/v1/operations/kafka/"
                + "dead-letter-command-audits/"
                + "{{auditCommandId}}"
        );

        assertThat(
            eventScript(
                timelineRequest,
                "prerequest"
            )
        ).contains(
            "operatorAccessToken",
            "auditCommandId",
            "uuidPattern"
        );

        String timelineTests =
            eventScript(timelineRequest, "test");

        assertThat(timelineTests)
            .contains(
                "response.commandId",
                "response.complete",
                "response.entries"
            );

        assertSafeAuditResponseAssertions(
            timelineTests
        );
    }

    @Test
    void shouldKeepPrivilegedAuditEnvironmentValuesSafe()
        throws IOException {
        JsonNode environment =
            objectMapper.readTree(
                ENVIRONMENT_PATH.toFile()
            );

        Map<String, String> values =
            StreamSupport.stream(
                    environment
                        .path("values")
                        .spliterator(),
                    false
                )
                .collect(
                    Collectors.toMap(
                        value ->
                            value.path("key").asText(),
                        value ->
                            value.path("value").asText(),
                        (left, right) -> right,
                        LinkedHashMap::new
                    )
                );

        assertThat(values)
            .containsEntry("auditPage", "0")
            .containsEntry("auditSize", "20")
            .containsEntry("operatorAccessToken", "")
            .containsEntry("deadLetterRecordId", "")
            .containsEntry("auditCommandId", "")
            .containsEntry("auditOperatorId", "");
    }

    private static void assertGetRequest(
        JsonNode item,
        String expectedRawUrl
    ) {
        JsonNode request = item.path("request");

        assertThat(request.path("method").asText())
            .isEqualTo("GET");

        assertThat(
            request
                .path("url")
                .path("raw")
                .asText()
        ).isEqualTo(expectedRawUrl);

        JsonNode bearer =
            request
                .path("auth")
                .path("bearer");

        assertThat(bearer.isArray())
            .isTrue();

        assertThat(
            StreamSupport.stream(
                    bearer.spliterator(),
                    false
                )
                .anyMatch(entry ->
                    "token".equals(
                        entry.path("key").asText()
                    )
                        && "{{operatorAccessToken}}".equals(
                            entry.path("value").asText()
                        )
                )
        ).isTrue();
    }

    private static Map<String, String> queryVariables(
        JsonNode item
    ) {
        Map<String, String> variables =
            new LinkedHashMap<>();

        item.path("request")
            .path("url")
            .path("query")
            .forEach(query ->
                variables.put(
                    query.path("key").asText(),
                    query.path("value").asText()
                )
            );

        return variables;
    }

    private static void assertSafeAuditResponseAssertions(
        String script
    ) {
        assertThat(script)
            .contains(
                "payload",
                "recordKey",
                "operatorEmail",
                "exceptionMessage",
                "stackTrace",
                "replayLeaseOwner"
            );
    }

    private static String eventScript(
        JsonNode item,
        String listen
    ) {
        return StreamSupport.stream(
                item.path("event").spliterator(),
                false
            )
            .filter(event ->
                listen.equals(
                    event.path("listen").asText()
                )
            )
            .findFirst()
            .map(event ->
                StreamSupport.stream(
                        event
                            .path("script")
                            .path("exec")
                            .spliterator(),
                        false
                    )
                    .map(JsonNode::asText)
                    .collect(Collectors.joining("\n"))
            )
            .orElseThrow(() ->
                new AssertionError(
                    "Missing " + listen + " script"
                )
            );
    }

    private static JsonNode findNamedItem(
        JsonNode items,
        String expectedName
    ) {
        return StreamSupport.stream(
                items.spliterator(),
                false
            )
            .filter(item ->
                expectedName.equals(
                    item.path("name").asText()
                )
            )
            .findFirst()
            .orElseThrow(() ->
                new AssertionError(
                    "Missing Postman item: "
                        + expectedName
                )
            );
    }
}
