package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MfaPostmanCollectionContractTest {

    private static final Path COLLECTION_PATH =
        Path.of(
            "postman",
            "PayFlow.mfa.postman_collection.json"
        );

    private static final Path ENVIRONMENT_PATH =
        Path.of(
            "postman",
            "PayFlow.local.postman_environment.json"
        );

    private static final Path README_PATH =
        Path.of("postman", "README.md");

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    @Test
    void shouldExposeOrderedManualMfaWorkflow()
        throws IOException {

        JsonNode collection = readCollection();

        assertThat(names(collection.path("item")))
            .containsExactly(
                "Enrollment",
                "Login Challenge",
                "Recovery-Code Rotation",
                "MFA Disable"
            );

        assertThat(names(folder(collection, "Enrollment")))
            .containsExactly(
                "Get MFA Status",
                "Begin MFA Enrollment",
                "Confirm MFA Enrollment"
            );

        assertThat(names(folder(collection, "Login Challenge")))
            .containsExactly(
                "Login With Enabled MFA",
                "Confirm MFA Login Challenge"
            );

        assertThat(names(folder(
            collection,
            "Recovery-Code Rotation"
        )))
            .containsExactly(
                "Issue Recovery-Code Rotation Grant",
                "Rotate MFA Recovery Codes"
            );

        assertThat(names(folder(collection, "MFA Disable")))
            .containsExactly(
                "Issue MFA Disable Grant",
                "Disable MFA (Destructive)"
            );
    }

    @Test
    void shouldUseExactMfaEndpointsAndRequestBodies()
        throws IOException {

        JsonNode collection = readCollection();

        assertBearerRequest(
            item(collection, "Enrollment", "Get MFA Status"),
            "GET",
            "{{baseUrl}}/api/v1/users/me/mfa"
        );

        JsonNode begin = item(
            collection,
            "Enrollment",
            "Begin MFA Enrollment"
        );
        assertBearerRequest(
            begin,
            "POST",
            "{{baseUrl}}/api/v1/users/me/mfa/enrollment"
        );
        assertThat(body(begin))
            .contains("{{mfaPassword}}")
            .doesNotContain("secret", "recoveryCodes");

        JsonNode confirmEnrollment = item(
            collection,
            "Enrollment",
            "Confirm MFA Enrollment"
        );
        assertBearerRequest(
            confirmEnrollment,
            "POST",
            "{{baseUrl}}/api/v1/users/me/mfa/enrollment/confirm"
        );
        assertThat(body(confirmEnrollment))
            .contains("{{mfaCode}}")
            .doesNotContain("mfaPassword");

        JsonNode login = item(
            collection,
            "Login Challenge",
            "Login With Enabled MFA"
        );
        assertNoAuthRequest(
            login,
            "POST",
            "{{baseUrl}}/api/v1/auth/login"
        );
        assertThat(body(login))
            .contains("{{mfaEmail}}", "{{mfaPassword}}");

        JsonNode confirmLogin = item(
            collection,
            "Login Challenge",
            "Confirm MFA Login Challenge"
        );
        assertNoAuthRequest(
            confirmLogin,
            "POST",
            "{{baseUrl}}/api/v1/auth/mfa/challenges/confirm"
        );
        assertThat(body(confirmLogin))
            .contains("{{mfaChallengeToken}}", "{{mfaCode}}")
            .doesNotContain("mfaPassword");

        JsonNode rotationGrant = item(
            collection,
            "Recovery-Code Rotation",
            "Issue Recovery-Code Rotation Grant"
        );
        assertStepUpRequest(
            rotationGrant,
            "recovery-code-rotation"
        );

        JsonNode rotation = item(
            collection,
            "Recovery-Code Rotation",
            "Rotate MFA Recovery Codes"
        );
        assertBearerRequest(
            rotation,
            "POST",
            "{{baseUrl}}/api/v1/users/me/mfa/recovery-codes/rotation"
        );
        assertThat(body(rotation))
            .contains("{{mfaStepUpGrant}}")
            .doesNotContain("mfaCode");

        JsonNode disableGrant = item(
            collection,
            "MFA Disable",
            "Issue MFA Disable Grant"
        );
        assertStepUpRequest(disableGrant, "mfa-disable");

        JsonNode disable = item(
            collection,
            "MFA Disable",
            "Disable MFA (Destructive)"
        );
        assertBearerRequest(
            disable,
            "DELETE",
            "{{baseUrl}}/api/v1/users/me/mfa"
        );
        assertThat(body(disable))
            .contains("{{mfaStepUpGrant}}")
            .doesNotContain("mfaCode");
    }

    @Test
    void shouldKeepOneTimeSecretsOutOfCollectionVariables()
        throws IOException {

        JsonNode collection = readCollection();

        String beginScript = eventScript(
            item(
                collection,
                "Enrollment",
                "Begin MFA Enrollment"
            ),
            "test"
        );

        assertThat(beginScript)
            .contains(
                "response.secret",
                "response.provisioningUri",
                "mfaEnrollmentSecret"
            )
            .doesNotContain(
                "pm.environment.set('mfaEnrollmentSecret'",
                "pm.collectionVariables.set('mfaEnrollmentSecret'"
            );

        String enrollmentScript = eventScript(
            item(
                collection,
                "Enrollment",
                "Confirm MFA Enrollment"
            ),
            "test"
        );

        String rotationScript = eventScript(
            item(
                collection,
                "Recovery-Code Rotation",
                "Rotate MFA Recovery Codes"
            ),
            "test"
        );

        assertThat(enrollmentScript)
            .contains("response.recoveryCodes", "lengthOf(10)")
            .doesNotContain(
                "pm.environment.set('mfaRecoveryCodes'",
                "pm.collectionVariables.set('mfaRecoveryCodes'"
            );

        assertThat(rotationScript)
            .contains(
                "Cache-Control",
                "no-store",
                "response.recoveryCodes",
                "pm.environment.unset('mfaStepUpGrant')"
            )
            .doesNotContain(
                "pm.environment.set('mfaRecoveryCodes'",
                "pm.collectionVariables.set('mfaRecoveryCodes'"
            );

        String disableDescription = item(
            collection,
            "MFA Disable",
            "Disable MFA (Destructive)"
        )
            .path("request")
            .path("description")
            .asText();

        assertThat(disableDescription)
            .contains(
                "DESTRUCTIVE",
                "removes authenticator and recovery-code state",
                "revokes active refresh-token families"
            );
    }

    @Test
    void shouldKeepCommittedMfaEnvironmentValuesEmpty()
        throws IOException {

        JsonNode environment = objectMapper.readTree(
            ENVIRONMENT_PATH.toFile()
        );

        Map<String, JsonNode> values =
            StreamSupport.stream(
                    environment.path("values").spliterator(),
                    false
                )
                .collect(
                    Collectors.toMap(
                        value -> value.path("key").asText(),
                        value -> value,
                        (left, right) -> right,
                        LinkedHashMap::new
                    )
                );

        assertThat(values)
            .containsKeys(
                "mfaEmail",
                "mfaPassword",
                "mfaAccessToken",
                "mfaCode",
                "mfaChallengeToken",
                "mfaStepUpGrant"
            )
            .doesNotContainKeys(
                "mfaEnrollmentSecret",
                "mfaRecoveryCodes"
            );

        assertThat(values.get("mfaEmail").path("type").asText())
            .isEqualTo("default");

        for (String secretKey : List.of(
            "mfaPassword",
            "mfaAccessToken",
            "mfaCode",
            "mfaChallengeToken",
            "mfaStepUpGrant"
        )) {
            assertThat(values.get(secretKey).path("type").asText())
                .isEqualTo("secret");
        }

        for (String key : List.of(
            "mfaEmail",
            "mfaPassword",
            "mfaAccessToken",
            "mfaCode",
            "mfaChallengeToken",
            "mfaStepUpGrant"
        )) {
            assertThat(values.get(key).path("value").asText())
                .isEmpty();
        }
    }

    @Test
    void shouldDocumentManualAndDestructiveBoundaries()
        throws IOException {

        String readme = Files.readString(README_PATH)
            .replaceAll("\\s+", " ")
            .trim();

        assertThat(readme)
            .contains(
                "PayFlow.mfa.postman_collection.json",
                "## MFA security workflow",
                "never export or commit the environment",
                "collection intentionally does not retain them",
                "Disable is destructive",
                "revokes active refresh-token families"
            );
    }

    private JsonNode readCollection() throws IOException {
        return objectMapper.readTree(COLLECTION_PATH.toFile());
    }

    private static JsonNode folder(
        JsonNode collection,
        String folderName
    ) {
        return findNamedItem(
            collection.path("item"),
            folderName
        ).path("item");
    }

    private static JsonNode item(
        JsonNode collection,
        String folderName,
        String itemName
    ) {
        return findNamedItem(
            folder(collection, folderName),
            itemName
        );
    }

    private static List<String> names(JsonNode items) {
        return StreamSupport.stream(
                items.spliterator(),
                false
            )
            .map(item -> item.path("name").asText())
            .toList();
    }

    private static void assertStepUpRequest(
        JsonNode item,
        String purpose
    ) {
        assertBearerRequest(
            item,
            "POST",
            "{{baseUrl}}/api/v1/users/me/step-up/grants"
        );
        assertThat(body(item))
            .contains(purpose, "{{mfaCode}}")
            .doesNotContain("mfaStepUpGrant");
    }

    private static void assertBearerRequest(
        JsonNode item,
        String method,
        String url
    ) {
        assertRequest(item, method, url, "bearer");

        assertThat(
            item
                .path("request")
                .path("auth")
                .path("bearer")
                .get(0)
                .path("value")
                .asText()
        ).isEqualTo("{{mfaAccessToken}}");
    }

    private static void assertNoAuthRequest(
        JsonNode item,
        String method,
        String url
    ) {
        assertRequest(item, method, url, "noauth");
    }

    private static void assertRequest(
        JsonNode item,
        String method,
        String url,
        String authType
    ) {
        JsonNode request = item.path("request");

        assertThat(request.path("method").asText())
            .isEqualTo(method);
        assertThat(request.path("url").path("raw").asText())
            .isEqualTo(url);
        assertThat(request.path("auth").path("type").asText())
            .isEqualTo(authType);
    }

    private static String body(JsonNode item) {
        return item
            .path("request")
            .path("body")
            .path("raw")
            .asText();
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
                listen.equals(event.path("listen").asText())
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
                expectedName.equals(item.path("name").asText())
            )
            .findFirst()
            .orElseThrow(() ->
                new AssertionError(
                    "Missing Postman item: " + expectedName
                )
            );
    }
}
