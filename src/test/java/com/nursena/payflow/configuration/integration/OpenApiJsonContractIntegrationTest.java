package com.nursena.payflow.configuration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet
    .request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet
    .result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet
    .result.MockMvcResultMatchers.status;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.configuration
    .OpenApiConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
    .AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers
    .PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OpenApiJsonContractIntegrationTest {

    private static final String SYSTEM_HEALTH_PATH =
        "/api/v1/system/health";

    private static final String REGISTER_PATH =
        "/api/v1/auth/register";

    private static final String LOGIN_PATH =
        "/api/v1/auth/login";

    private static final String USER_PROFILE_PATH =
        "/api/v1/users/me";

    private static final String WALLETS_PATH =
        "/api/v1/wallets";

    private static final String CURRENT_WALLET_PATH =
        "/api/v1/wallets/me";

    private static final String TOP_UP_PATH =
        "/api/v1/wallets/me/top-ups";

    private static final String TRANSFERS_PATH =
        "/api/v1/transfers";

    private static final String TRANSACTIONS_PATH =
        "/api/v1/transactions/me";

    private static final String
        KAFKA_DEAD_LETTERS_PATH =
        "/api/v1/operations/kafka/dead-letters";

    private static final String
        KAFKA_DEAD_LETTER_DETAILS_PATH =
        KAFKA_DEAD_LETTERS_PATH
            + "/{recordId}";

    private static final String
        KAFKA_DEAD_LETTER_REPLAY_PATH =
        KAFKA_DEAD_LETTERS_PATH
            + "/{recordId}/replay";

    private static final String
        KAFKA_DEAD_LETTER_DISCARD_PATH =
        KAFKA_DEAD_LETTERS_PATH
            + "/{recordId}/discard";
    private static final String
        KAFKA_DEAD_LETTER_COMMAND_AUDITS_PATH =
        "/api/v1/operations/kafka/dead-letter-command-audits";
    private static final String
        KAFKA_DEAD_LETTER_COMMAND_AUDIT_TIMELINE_PATH =
        KAFKA_DEAD_LETTER_COMMAND_AUDITS_PATH
            + "/{commandId}";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode openApi;

    @BeforeEach
    void loadOpenApiDocument() throws Exception {
        MvcResult result =
            mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                    content()
                        .contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                        )
                )
                .andReturn();

        openApi =
            objectMapper.readTree(
                result
                    .getResponse()
                    .getContentAsByteArray()
            );
    }

    @Test
    void shouldExposeApiMetadataAndBearerScheme() {
        assertThat(openApi.path("openapi").asText())
            .startsWith("3.");

        JsonNode info = openApi.path("info");

        assertThat(info.path("title").asText())
            .isEqualTo("PayFlow API");

        assertThat(info.path("version").asText())
            .isEqualTo("0.2.0");

        assertThat(info.path("description").asText())
            .contains(
                "PayFlow does not process real money."
            );

        assertThat(openApi.has("security"))
            .isFalse();

        JsonNode bearerScheme =
            openApi
                .path("components")
                .path("securitySchemes")
                .path(
                    OpenApiConfiguration
                        .BEARER_AUTH_SCHEME
                );

        assertThat(bearerScheme.isObject())
            .isTrue();

        assertThat(bearerScheme.path("type").asText())
            .isEqualTo("http");

        assertThat(bearerScheme.path("scheme").asText())
            .isEqualTo("bearer");

        assertThat(
            bearerScheme
                .path("bearerFormat")
                .asText()
        ).isEqualTo("JWT");
    }

    @Test
    void shouldExposeExactlyThePublicApiPaths() {
        assertThat(
            fieldNames(openApi.path("paths"))
        ).containsExactlyInAnyOrder(
            SYSTEM_HEALTH_PATH,
            REGISTER_PATH,
            LOGIN_PATH,
            USER_PROFILE_PATH,
            WALLETS_PATH,
            CURRENT_WALLET_PATH,
            TOP_UP_PATH,
            TRANSFERS_PATH,
            TRANSACTIONS_PATH,
            KAFKA_DEAD_LETTERS_PATH,
            KAFKA_DEAD_LETTER_DETAILS_PATH,
            KAFKA_DEAD_LETTER_REPLAY_PATH,
            KAFKA_DEAD_LETTER_DISCARD_PATH,
            KAFKA_DEAD_LETTER_COMMAND_AUDITS_PATH,
            KAFKA_DEAD_LETTER_COMMAND_AUDIT_TIMELINE_PATH
        );

        JsonNode health =
            operation(
                SYSTEM_HEALTH_PATH,
                "get"
            );

        assertPublicOperation(health);
        assertResponseCodes(health, "200");

        JsonNode register =
            operation(
                REGISTER_PATH,
                "post"
            );

        assertPublicOperation(register);
        assertResponseCodes(
            register,
            "201",
            "400",
            "409"
        );

        JsonNode login =
            operation(
                LOGIN_PATH,
                "post"
            );

        assertPublicOperation(login);
        assertResponseCodes(
            login,
            "200",
            "400",
            "401",
            "403"
        );
    }

    @Test
    void shouldExposeAuthenticatedApiOperations() {
        JsonNode profile =
            operation(
                USER_PROFILE_PATH,
                "get"
            );

        assertAuthenticatedOperation(
            profile,
            "getCurrentUserProfile",
            new String[] {
                "200",
                "401",
                "404"
            }
        );

        assertParameterNames(profile);

        JsonNode openWallet =
            operation(
                WALLETS_PATH,
                "post"
            );

        assertAuthenticatedOperation(
            openWallet,
            "openWallet",
            new String[] {
                "201",
                "400",
                "401",
                "409"
            }
        );

        assertParameterNames(openWallet);

        JsonNode currentWallet =
            operation(
                CURRENT_WALLET_PATH,
                "get"
            );

        assertAuthenticatedOperation(
            currentWallet,
            "getCurrentWallet",
            new String[] {
                "200",
                "401",
                "404"
            }
        );

        assertParameterNames(currentWallet);

        JsonNode topUp =
            operation(
                TOP_UP_PATH,
                "post"
            );

        assertAuthenticatedOperation(
            topUp,
            "topUpCurrentWallet",
            new String[] {
                "200",
                "400",
                "401",
                "404",
                "409",
                "422"
            }
        );

        assertParameterNames(topUp);

        JsonNode transfer =
            operation(
                TRANSFERS_PATH,
                "post"
            );

        assertAuthenticatedOperation(
            transfer,
            "transferMoney",
            new String[] {
                "201",
                "400",
                "401",
                "404",
                "409",
                "422"
            }
        );

        assertParameterNames(
            transfer,
            "Idempotency-Key"
        );

        JsonNode history =
            operation(
                TRANSACTIONS_PATH,
                "get"
            );

        assertAuthenticatedOperation(
            history,
            "getTransactionHistory",
            new String[] {
                "200",
                "400",
                "401",
                "404"
            }
        );

        assertParameterNames(
            history,
            "page",
            "size",
            "direction",
            "status",
            "from",
            "to"
        );
        JsonNode deadLetters =
            operation(
                KAFKA_DEAD_LETTERS_PATH,
                "get"
            );

        assertAuthenticatedOperation(
            deadLetters,
            "listKafkaDeadLetterRecords",
            new String[] {
                "200",
                "400",
                "401",
                "403"
            }
        );

        assertParameterNames(
            deadLetters,
            "page",
            "size",
            "status"
        );

        JsonNode deadLetterDetails =
            operation(
                KAFKA_DEAD_LETTER_DETAILS_PATH,
                "get"
            );

        JsonNode replayDeadLetter =
            operation(
                KAFKA_DEAD_LETTER_REPLAY_PATH,
                "post"
            );

        assertAuthenticatedOperation(
            replayDeadLetter,
            "replayKafkaDeadLetterRecord",
            new String[] {
                "200",
                "400",
                "401",
                "403",
                "404",
                "409",
                "500",
                "502",
                "503"
            }
        );

        assertParameterNames(
            replayDeadLetter,
            "recordId"
        );

        JsonNode discardDeadLetter =
            operation(
                KAFKA_DEAD_LETTER_DISCARD_PATH,
                "post"
            );

        assertAuthenticatedOperation(
            discardDeadLetter,
            "discardKafkaDeadLetterRecord",
            new String[] {
                "204",
                "400",
                "401",
                "403",
                "404",
                "409",
                "500",
                "503"
            }
        );

        assertParameterNames(
            discardDeadLetter,
            "recordId"
        );

        assertAuthenticatedOperation(
            deadLetterDetails,
            "getKafkaDeadLetterRecord",
            new String[] {
                "200",
                "400",
                "401",
                "403",
                "404"
            }
        );

        assertParameterNames(
            deadLetterDetails,
            "recordId"
        );
    }

    @Test
    void shouldExposeLoginCredentialPairContract() {
        JsonNode login =
            operation(
                LOGIN_PATH,
                "post"
            );

        JsonNode successSchema =
            login
                .path("responses")
                .path("200")
                .path("content")
                .path(
                    MediaType.APPLICATION_JSON_VALUE
                )
                .path("schema");

        assertThat(
            successSchema
                .path("$ref")
                .asText()
        ).isEqualTo(
            "#/components/schemas/"
                + "AuthenticateUserResponse"
        );

        JsonNode properties =
            openApi
                .path("components")
                .path("schemas")
                .path(
                    "AuthenticateUserResponse"
                )
                .path("properties");

        assertThat(
            fieldNames(properties)
        )
            .containsExactlyInAnyOrder(
                "accessToken",
                "tokenType",
                "expiresAt",
                "refreshToken",
                "refreshTokenExpiresAt"
            )
            .doesNotContain(
                "tokenDigest",
                "familyId",
                "recordId",
                "userId",
                "revokedAt",
                "consumedAt",
                "successorId"
            );

        assertThat(
            properties
                .path("expiresAt")
                .path("format")
                .asText()
        ).isEqualTo("date-time");

        assertThat(
            properties
                .path(
                    "refreshTokenExpiresAt"
                )
                .path("format")
                .asText()
        ).isEqualTo("date-time");

        assertThat(
            properties
                .path("accessToken")
                .path("type")
                .asText()
        ).isEqualTo("string");

        assertThat(
            properties
                .path("refreshToken")
                .path("type")
                .asText()
        ).isEqualTo("string");
    }
    @Test
    void shouldExposeTransferContract() {
        JsonNode transfer =
            operation(
                TRANSFERS_PATH,
                "post"
            );

        JsonNode idempotencyKey =
            findParameter(
                transfer,
                "Idempotency-Key"
            );

        assertThat(idempotencyKey.path("in").asText())
            .isEqualTo("header");

        assertThat(
            idempotencyKey
                .path("required")
                .asBoolean()
        ).isTrue();

        JsonNode schema =
            idempotencyKey.path("schema");

        assertThat(schema.path("type").asText())
            .isEqualTo("string");

        assertThat(schema.path("minLength").asInt())
            .isEqualTo(1);

        assertThat(schema.path("maxLength").asInt())
            .isEqualTo(100);

        assertExampleNames(
            transfer,
            "400",
            "missingIdempotencyKey",
            "validationFailed"
        );

        assertExampleNames(
            transfer,
            "409",
            "idempotencyKeyConflict",
            "requestInProgress",
            "concurrentWalletUpdate"
        );

        assertExampleNames(
            transfer,
            "422",
            "invalidIdempotencyKey",
            "insufficientBalance",
            "walletNotActive",
            "currencyMismatch"
        );

        JsonNode schemas =
            openApi
                .path("components")
                .path("schemas");

        JsonNode responseProperties =
            schemas
                .path("TransferMoneyResponse")
                .path("properties");

        assertThat(
            fieldNames(responseProperties)
        ).doesNotContain("idempotencyKey");
    }

    @Test
    void shouldExposeTransactionHistoryContract() {
        JsonNode history =
            operation(
                TRANSACTIONS_PATH,
                "get"
            );

        JsonNode page =
            findParameter(history, "page");

        assertQueryParameter(page);

        assertThat(
            page.path("schema")
                .path("minimum")
                .asInt()
        ).isZero();

        assertThat(
            page.path("schema")
                .path("default")
                .asInt()
        ).isZero();

        JsonNode size =
            findParameter(history, "size");

        assertQueryParameter(size);

        assertThat(
            size.path("schema")
                .path("minimum")
                .asInt()
        ).isEqualTo(1);

        assertThat(
            size.path("schema")
                .path("maximum")
                .asInt()
        ).isEqualTo(100);

        assertThat(
            size.path("schema")
                .path("default")
                .asInt()
        ).isEqualTo(20);

        JsonNode direction =
            findParameter(
                history,
                "direction"
            );

        assertQueryParameter(direction);

        assertThat(
            textValues(
                direction
                    .path("schema")
                    .path("enum")
            )
        ).containsExactlyInAnyOrder(
            "INCOMING",
            "OUTGOING"
        );

        JsonNode transactionStatus =
            findParameter(
                history,
                "status"
            );

        assertQueryParameter(transactionStatus);

        assertThat(
            textValues(
                transactionStatus
                    .path("schema")
                    .path("enum")
            )
        ).containsExactlyInAnyOrder(
            "PENDING",
            "COMPLETED",
            "FAILED"
        );

        JsonNode from =
            findParameter(history, "from");

        assertQueryParameter(from);

        assertThat(
            from.path("description").asText()
        ).containsIgnoringCase("inclusive");

        assertThat(
            from.path("schema")
                .path("format")
                .asText()
        ).isEqualTo("date-time");

        JsonNode to =
            findParameter(history, "to");

        assertQueryParameter(to);

        assertThat(
            to.path("description").asText()
        ).containsIgnoringCase("exclusive");

        assertThat(
            to.path("schema")
                .path("format")
                .asText()
        ).isEqualTo("date-time");

        JsonNode historyItemProperties =
            openApi
                .path("components")
                .path("schemas")
                .path(
                    "TransactionHistoryItemResponse"
                )
                .path("properties");

        assertThat(
            fieldNames(historyItemProperties)
        ).doesNotContain("idempotencyKey");
    }

    @Test
    void shouldExposeKafkaDeadLetterQueryContract() {
        JsonNode deadLetters =
            operation(
                KAFKA_DEAD_LETTERS_PATH,
                "get"
            );

        JsonNode page =
            findParameter(
                deadLetters,
                "page"
            );

        assertQueryParameter(page);

        assertThat(
            page.path("schema")
                .path("minimum")
                .asInt()
        ).isZero();

        assertThat(
            page.path("schema")
                .path("default")
                .asInt()
        ).isZero();

        JsonNode size =
            findParameter(
                deadLetters,
                "size"
            );

        assertQueryParameter(size);

        assertThat(
            size.path("schema")
                .path("minimum")
                .asInt()
        ).isEqualTo(1);

        assertThat(
            size.path("schema")
                .path("maximum")
                .asInt()
        ).isEqualTo(100);

        assertThat(
            size.path("schema")
                .path("default")
                .asInt()
        ).isEqualTo(20);

        JsonNode status =
            findParameter(
                deadLetters,
                "status"
            );

        assertQueryParameter(status);

        assertThat(
            textValues(
                status.path("schema")
                    .path("enum")
            )
        ).containsExactlyInAnyOrder(
            "RECEIVED",
            "REPLAYING",
            "REPLAYED",
            "REPLAY_FAILED",
            "DISCARDED"
        );

        JsonNode deadLetterDetails =
            operation(
                KAFKA_DEAD_LETTER_DETAILS_PATH,
                "get"
            );

        JsonNode recordId =
            findParameter(
                deadLetterDetails,
                "recordId"
            );

        assertUuidPathParameter(recordId);

        JsonNode schemas =
            openApi
                .path("components")
                .path("schemas");

        JsonNode summaryProperties =
            schemas
                .path(
                    "KafkaDeadLetterRecordSummaryResponse"
                )
                .path("properties");

        assertThat(
            fieldNames(summaryProperties)
        )
            .contains(
                "id",
                "status",
                "deadLetterTopic",
                "originalTopic",
                "replayCount",
                "replayAttemptBase",
                "totalReplayAttempts",
                "payloadAvailable"
            )
            .doesNotContain(
                "payload",
                "recordKey",
                "replayLeaseOwner"
            );

        JsonNode detailsProperties =
            schemas
                .path(
                    "KafkaDeadLetterRecordDetailsResponse"
                )
                .path("properties");

        assertThat(
            fieldNames(detailsProperties)
        )
            .contains(
                "id",
                "status",
                "exceptionMessage",
                "lastReplayError",
                "replayLeaseUntil",
                "payloadAvailable"
            )
            .doesNotContain(
                "summary",
                "payload",
                "recordKey",
                "replayLeaseOwner"
            );

        JsonNode pageProperties =
            schemas
                .path(
                    "KafkaDeadLetterRecordPageResponse"
                )
                .path("properties");

        assertThat(
            fieldNames(pageProperties)
        ).containsExactlyInAnyOrder(
            "items",
            "page",
            "size",
            "totalElements",
            "totalPages",
            "first",
            "last",
            "hasNext",
            "hasPrevious"
        );
    }

    @Test
    void shouldExposeKafkaDeadLetterCommandContract() {
        JsonNode replay =
            operation(
                KAFKA_DEAD_LETTER_REPLAY_PATH,
                "post"
            );

        JsonNode replayRecordId =
            findParameter(
                replay,
                "recordId"
            );

        assertUuidPathParameter(replayRecordId);

        JsonNode replaySuccess =
            replay
                .path("responses")
                .path("200")
                .path("content")
                .path(
                    MediaType.APPLICATION_JSON_VALUE
                )
                .path("schema");

        assertThat(
            replaySuccess.path("$ref").asText()
        ).isEqualTo(
            "#/components/schemas/"
                + "KafkaDeadLetterReplayResponse"
        );

        JsonNode replayProperties =
            openApi
                .path("components")
                .path("schemas")
                .path(
                    "KafkaDeadLetterReplayResponse"
                )
                .path("properties");

        assertThat(
            fieldNames(replayProperties)
        )
            .containsExactlyInAnyOrder(
                "recordId",
                "status"
            )
            .doesNotContain(
                "payload",
                "recordKey",
                "replayLeaseOwner",
                "exceptionMessage",
                "lastReplayError"
            );

        JsonNode replayResponseRecordId =
            replayProperties.path("recordId");

        assertThat(
            replayResponseRecordId
                .path("type")
                .asText()
        ).isEqualTo("string");

        assertThat(
            replayResponseRecordId
                .path("format")
                .asText()
        ).isEqualTo("uuid");

        JsonNode replayStatus =
            replayProperties.path("status");

        assertThat(
            replayStatus.path("type").asText()
        ).isEqualTo("string");

        assertThat(
            textValues(
                replayStatus.path("enum")
            )
        ).containsExactly("REPLAYED");

        JsonNode discard =
            operation(
                KAFKA_DEAD_LETTER_DISCARD_PATH,
                "post"
            );

        JsonNode discardRecordId =
            findParameter(
                discard,
                "recordId"
            );

        assertUuidPathParameter(discardRecordId);

        JsonNode discardSuccess =
            discard
                .path("responses")
                .path("204");

        assertThat(discardSuccess.isObject())
            .isTrue();

        assertThat(discardSuccess.has("content"))
            .isFalse();
    }


    private JsonNode operation(
        String path,
        String method
    ) {
        JsonNode operation =
            openApi
                .path("paths")
                .path(path)
                .path(method);

        assertThat(operation.isObject())
            .as(
                "%s %s operation must exist",
                method.toUpperCase(),
                path
            )
            .isTrue();

        return operation;
    }

    private static void assertPublicOperation(
        JsonNode operation
    ) {
        assertThat(operation.has("security"))
            .isFalse();
    }

    private static void assertAuthenticatedOperation(
        JsonNode operation,
        String expectedOperationId,
        String[] expectedResponseCodes
    ) {
        assertThat(
            operation
                .path("operationId")
                .asText()
        ).isEqualTo(expectedOperationId);

        JsonNode security =
            operation.path("security");

        assertThat(security.isArray())
            .isTrue();

        boolean bearerSecurityPresent =
            StreamSupport.stream(
                    security.spliterator(),
                    false
                )
                .anyMatch(requirement ->
                    requirement.has(
                        OpenApiConfiguration
                            .BEARER_AUTH_SCHEME
                    )
                );

        assertThat(bearerSecurityPresent)
            .isTrue();

        assertResponseCodes(
            operation,
            expectedResponseCodes
        );
    }

    private static void assertResponseCodes(
        JsonNode operation,
        String... expectedCodes
    ) {
        JsonNode responses =
            operation.path("responses");

        assertThat(responses.isObject())
            .isTrue();

        assertThat(fieldNames(responses))
            .containsExactlyInAnyOrder(
                expectedCodes
            );
    }

    private static void assertParameterNames(
        JsonNode operation,
        String... expectedNames
    ) {
        assertThat(
            parameterNames(operation)
        ).containsExactlyInAnyOrder(
            expectedNames
        );
    }

    private static Set<String> parameterNames(
        JsonNode operation
    ) {
        Set<String> names =
            new LinkedHashSet<>();

        JsonNode parameters =
            operation.path("parameters");

        if (!parameters.isArray()) {
            return names;
        }

        parameters.forEach(parameter ->
            names.add(
                parameter
                    .path("name")
                    .asText()
            )
        );

        return names;
    }

    private static JsonNode findParameter(
        JsonNode operation,
        String expectedName
    ) {
        return StreamSupport.stream(
                operation
                    .path("parameters")
                    .spliterator(),
                false
            )
            .filter(parameter ->
                expectedName.equals(
                    parameter
                        .path("name")
                        .asText()
                )
            )
            .findFirst()
            .orElseThrow(() ->
                new AssertionError(
                    "OpenAPI parameter is missing: "
                        + expectedName
                )
            );
    }
    private static void assertUuidPathParameter(
        JsonNode parameter
    ) {
        assertThat(parameter.path("in").asText())
            .isEqualTo("path");

        assertThat(
            parameter.path("required").asBoolean()
        ).isTrue();

        assertThat(
            parameter.path("schema")
                .path("type")
                .asText()
        ).isEqualTo("string");

        assertThat(
            parameter.path("schema")
                .path("format")
                .asText()
        ).isEqualTo("uuid");
    }

    private static void assertQueryParameter(
        JsonNode parameter
    ) {
        assertThat(parameter.path("in").asText())
            .isEqualTo("query");
    }

    private static void assertExampleNames(
        JsonNode operation,
        String responseCode,
        String... expectedNames
    ) {
        JsonNode examples =
            operation
                .path("responses")
                .path(responseCode)
                .path("content")
                .path(
                    MediaType
                        .APPLICATION_JSON_VALUE
                )
                .path("examples");

        assertThat(examples.isObject())
            .isTrue();

        assertThat(fieldNames(examples))
            .containsExactlyInAnyOrder(
                expectedNames
            );
    }

    private static Set<String> fieldNames(
        JsonNode object
    ) {
        Set<String> names =
            new LinkedHashSet<>();

        object
            .fieldNames()
            .forEachRemaining(names::add);

        return names;
    }

    private static Set<String> textValues(
        JsonNode array
    ) {
        Set<String> values =
            new LinkedHashSet<>();

        array.forEach(value ->
            values.add(value.asText())
        );

        return values;
    }
}
