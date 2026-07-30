package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LoginRateLimitPostmanCollectionContractTest {

    private static final Path COLLECTION_PATH =
        Path.of(
            "postman",
            "PayFlow.login-rate-limit.postman_collection.json"
        );

    private static final String FOLDER_NAME =
        "Identity Threshold";

    private static final String LOGIN_URL =
        "{{baseUrl}}/api/v1/auth/login";

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    @Test
    void shouldExposeIdentityThresholdWorkflow()
        throws IOException {

        JsonNode collection =
            objectMapper.readTree(
                COLLECTION_PATH.toFile()
            );

        JsonNode folder =
            findNamedItem(
                collection.path("item"),
                FOLDER_NAME
            );

        assertThat(
            eventScript(folder, "prerequest")
        )
            .contains(
                "Failed Login Attempt 1",
                "Date.now()",
                "rateLimitEmail"
            )
            .doesNotContain(
                "accessToken",
                "refreshToken",
                "passwordHash"
            );

        List<JsonNode> attempts =
            StreamSupport.stream(
                    folder
                        .path("item")
                        .spliterator(),
                    false
                )
                .toList();

        assertThat(attempts)
            .extracting(
                attempt ->
                    attempt.path("name").asText()
            )
            .containsExactly(
                "Failed Login Attempt 1",
                "Failed Login Attempt 2",
                "Failed Login Attempt 3",
                "Failed Login Attempt 4",
                "Failed Login Attempt 5",
                "Failed Login Attempt 6"
            );

        for (int index = 0; index < 5; index++) {
            JsonNode attempt = attempts.get(index);

            assertLoginRequest(attempt);

            assertThat(
                eventScript(attempt, "test")
            )
                .contains(
                    "pm.response.to.have.status(401)",
                    "INVALID_CREDENTIALS",
                    "Email or password is incorrect.",
                    "pm.expect(response).not.to.have.property('userExists')"
                )
                .doesNotContain(
                    "emailExists"
                );
        }

        JsonNode blockedAttempt =
            attempts.get(5);

        assertLoginRequest(blockedAttempt);

        assertThat(
            eventScript(blockedAttempt, "test")
        )
            .contains(
                "pm.response.to.have.status(429)",
                "Retry-After",
                "LOGIN_RATE_LIMIT_EXCEEDED",
                "Too many login attempts. Try again later.",
                "pm.collectionVariables.unset('rateLimitEmail')",
                "pm.expect(response).not.to.have.property('userExists')"
            )
            .doesNotContain(
                "emailExists"
            );
    }

    @Test
    void shouldKeepCollectionVariablesCredentialFree()
        throws IOException {

        JsonNode collection =
            objectMapper.readTree(
                COLLECTION_PATH.toFile()
            );

        Map<String, String> variables =
            StreamSupport.stream(
                    collection
                        .path("variable")
                        .spliterator(),
                    false
                )
                .collect(
                    Collectors.toMap(
                        variable ->
                            variable.path("key").asText(),
                        variable ->
                            variable.path("value").asText(),
                        (left, right) -> right,
                        LinkedHashMap::new
                    )
                );

        assertThat(variables)
            .containsEntry(
                "baseUrl",
                "http://localhost:8080"
            )
            .containsEntry(
                "rateLimitEmail",
                ""
            )
            .doesNotContainKeys(
                "accessToken",
                "refreshToken",
                "password",
                "operatorAccessToken"
            );
    }

    private static void assertLoginRequest(
        JsonNode item
    ) {
        JsonNode request =
            item.path("request");

        assertThat(
            request.path("method").asText()
        )
            .isEqualTo("POST");

        assertThat(
            request
                .path("url")
                .path("raw")
                .asText()
        )
            .isEqualTo(LOGIN_URL);

        String body =
            request
                .path("body")
                .path("raw")
                .asText();

        assertThat(body)
            .contains(
                "{{rateLimitEmail}}",
                "WrongPassword123!"
            )
            .doesNotContain(
                "accessToken",
                "refreshToken"
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
                    .collect(
                        Collectors.joining("\n")
                    )
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
