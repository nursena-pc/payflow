package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class V016ApiDocumentationAlignmentContractTest {

    private static final Path BASELINE =
        Path.of("docs", "api-v1-compatibility.md");

    private static final Path ARCHITECTURE =
        Path.of("docs", "architecture.md");

    private static final Path README =
        Path.of("README.md");

    private static final Path POSTMAN_README =
        Path.of("postman", "README.md");

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

    private static final List<Path> POSTMAN_COLLECTIONS =
        List.of(
            Path.of(
                "postman",
                "PayFlow.postman_collection.json"
            ),
            Path.of(
                "postman",
                "PayFlow.mfa.postman_collection.json"
            ),
            Path.of(
                "postman",
                "PayFlow.login-rate-limit.postman_collection.json"
            ),
            Path.of(
                "postman",
                "PayFlow.api-compatibility.postman_collection.json"
            )
        );

    private static final Path COMPATIBILITY_COLLECTION =
        Path.of(
            "postman",
            "PayFlow.api-compatibility.postman_collection.json"
        );

    private static final Pattern BASELINE_OPERATION =
        Pattern.compile(
            "^\\|\\s*`(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)`"
                + "\\s*\\|\\s*`([^`]+)`\\s*\\|"
        );

    private static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper();

    @Test
    void shouldCoverEveryCanonicalOperationAcrossPostmanAssets()
        throws IOException {

        Set<String> canonical = canonicalOperations();
        Set<String> postman = postmanOperations(POSTMAN_COLLECTIONS);

        assertThat(canonical).hasSize(30);
        assertThat(postman)
            .containsExactlyInAnyOrderElementsOf(canonical);

        assertThat(postmanOperations(List.of(COMPATIBILITY_COLLECTION)))
            .containsExactlyInAnyOrder(
                "DELETE /api/v1/users/me/mfa/enrollment",
                "POST /api/v1/auth/email-verification/confirm",
                "POST /api/v1/auth/logout",
                "POST /api/v1/auth/logout-all",
                "POST /api/v1/auth/refresh"
            );
    }

    @Test
    void shouldKeepOpenApiAndDocumentationMetadataAligned()
        throws IOException {

        String openApi = Files.readString(OPENAPI_CONFIGURATION);
        String architecture = Files.readString(ARCHITECTURE);
        String baseline = Files.readString(BASELINE);
        String readme = Files.readString(README);
        String postmanReadme = Files.readString(POSTMAN_README);

        assertThat(openApi)
            .contains("API_VERSION")
            .doesNotContain("\"0.2.0\"");

        assertThat(architecture)
            .contains(
                "Transactional outbox persistence is implemented",
                "`abuseprotection`",
                "`eventprocessing`",
                "`maildelivery`",
                "`observability`",
                "`outbox`",
                "at-least-once delivery boundary"
            )
            .doesNotContain(
                "Transactional outbox persistence is planned for a later milestone",
                "notification",
                "future messaging integrations"
            );

        assertThat(baseline)
            .contains(
                "Increment 5 alignment",
                "**30 unique canonical `/api/v1` operations**",
                "PayFlow.api-compatibility.postman_collection.json",
                "`0.16.0-SNAPSHOT`"
            );

        assertThat(readme)
            .contains(
                "PayFlow.api-compatibility.postman_collection.json",
                "30 canonical `/api/v1` operations"
            );

        assertThat(postmanReadme)
            .contains(
                "PayFlow.api-compatibility.postman_collection.json",
                "emailVerificationCredential",
                "refreshToken"
            );
    }

    @Test
    void shouldPreserveFrozenSecurityAndNonGoalBoundaries()
        throws IOException {

        String baseline = Files.readString(BASELINE);
        String architecture = Files.readString(ARCHITECTURE);

        assertThat(baseline)
            .contains(
                "Registration remains the reviewed `DEFER` case",
                "simulated-money modular monolith",
                "does not activate generalized registration abuse protection"
            );

        assertThat(architecture)
            .contains(
                "PostgreSQL remains the system of record",
                "password-login limiter remains a separate compatibility contract",
                "Registration remains outside generalized abuse-protection wiring"
            );
    }

    private static Set<String> canonicalOperations()
        throws IOException {

        Set<String> operations = new TreeSet<>();

        for (String line : Files.readAllLines(BASELINE)) {
            Matcher matcher = BASELINE_OPERATION.matcher(line);

            if (matcher.find()) {
                operations.add(
                    operationKey(
                        matcher.group(1),
                        matcher.group(2)
                    )
                );
            }
        }

        return operations;
    }

    private static Set<String> postmanOperations(
        List<Path> collections
    ) throws IOException {

        Set<String> operations = new TreeSet<>();

        for (Path collection : collections) {
            JsonNode root =
                OBJECT_MAPPER.readTree(
                    Files.readString(collection)
                );

            collectPostmanOperations(root, operations);
        }

        return operations;
    }

    private static void collectPostmanOperations(
        JsonNode node,
        Set<String> operations
    ) {
        if (node.isArray()) {
            node.forEach(
                child ->
                    collectPostmanOperations(
                        child,
                        operations
                    )
            );
            return;
        }

        if (!node.isObject()) {
            return;
        }

        JsonNode request = node.path("request");

        if (request.isObject()) {
            String method =
                request.path("method").asText();
            String rawUrl =
                request.path("url").path("raw").asText();
            String path = normalizePath(rawUrl);

            if (!method.isBlank()
                && path.startsWith("/api/v1")) {

                operations.add(
                    method.toUpperCase()
                        + " "
                        + path
                );
            }
        }

        JsonNode items = node.path("item");
        if (items.isArray()) {
            collectPostmanOperations(items, operations);
        }
    }

    private static String operationKey(
        String method,
        String route
    ) {
        return method.toUpperCase()
            + " "
            + normalizePath(route);
    }

    private static String normalizePath(String raw) {
        String path = raw.trim();

        if (path.startsWith("{{baseUrl}}")) {
            path =
                path.substring(
                    "{{baseUrl}}".length()
                );
        }

        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }

        path = path.replaceAll(
            "/\\{\\{[^/}]+}}",
            "/{id}"
        );
        path = path.replaceAll(
            "/\\{[^/{}]+}",
            "/{id}"
        );

        if (path.length() > 1
            && path.endsWith("/")) {
            path =
                path.substring(
                    0,
                    path.length() - 1
                );
        }

        return path;
    }
}
