package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import com.nursena.payflow.user.adapter.in.web.CurrentUserProfileController;
import com.nursena.payflow.wallet.adapter.in.web.GetCurrentWalletController;
import com.nursena.payflow.wallet.adapter.in.web.OpenWalletController;
import com.nursena.payflow.wallet.adapter.in.web.OpenWalletRequest;
import com.nursena.payflow.wallet.adapter.in.web.TopUpWalletController;
import com.nursena.payflow.wallet.adapter.in.web.TopUpWalletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import com.nursena.payflow.transaction.adapter.in.web.TransferMoneyController;
import com.nursena.payflow.transaction.adapter.in.web.TransferMoneyRequest;
import org.springframework.web.bind.annotation.RequestHeader;
import java.time.Instant;

import com.nursena.payflow.transaction.adapter.in.web.GetTransactionHistoryController;
import com.nursena.payflow.transaction.application.model.TransactionDirection;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;


class AuthenticatedApiDocumentationTest {

    @Test
    void shouldDocumentCurrentUserProfile()
        throws NoSuchMethodException {

        Method method =
            CurrentUserProfileController.class
                .getDeclaredMethod(
                    "getCurrentUserProfile",
                    Jwt.class
                );

        assertAuthenticatedDocumentation(
            CurrentUserProfileController.class,
            method,
            "Users",
            "getCurrentUserProfile",
            "200",
            "401",
            "404"
        );
    }

    @Test
    void shouldDocumentWalletOpening()
        throws NoSuchMethodException {

        Method method =
            OpenWalletController.class
                .getDeclaredMethod(
                    "openWallet",
                    Jwt.class,
                    OpenWalletRequest.class
                );

        assertAuthenticatedDocumentation(
            OpenWalletController.class,
            method,
            "Wallets",
            "openWallet",
            "201",
            "400",
            "401",
            "409"
        );
    }

    @Test
    void shouldDocumentCurrentWallet()
        throws NoSuchMethodException {

        Method method =
            GetCurrentWalletController.class
                .getDeclaredMethod(
                    "getCurrentWallet",
                    Jwt.class
                );

        assertAuthenticatedDocumentation(
            GetCurrentWalletController.class,
            method,
            "Wallets",
            "getCurrentWallet",
            "200",
            "401",
            "404"
        );
    }

    @Test
    void shouldDocumentWalletTopUp()
        throws NoSuchMethodException {

        Method method =
            TopUpWalletController.class
                .getDeclaredMethod(
                    "topUpWallet",
                    Jwt.class,
                    TopUpWalletRequest.class
                );

        assertAuthenticatedDocumentation(
            TopUpWalletController.class,
            method,
            "Wallets",
            "topUpCurrentWallet",
            "200",
            "400",
            "401",
            "404",
            "409",
            "422"
        );
    }

    private static void assertAuthenticatedDocumentation(
        Class<?> controllerType,
        Method method,
        String expectedTag,
        String expectedOperationId,
        String... expectedResponseCodes
    ) {
        Tag tag =
            controllerType.getAnnotation(Tag.class);

        assertThat(tag)
            .isNotNull();

        assertThat(tag.name())
            .isEqualTo(expectedTag);

        Operation operation =
            method.getAnnotation(Operation.class);

        assertThat(operation)
            .isNotNull();

        assertThat(operation.operationId())
            .isEqualTo(expectedOperationId);

        assertThat(operation.summary())
            .isNotBlank();

        SecurityRequirement security =
            method.getAnnotation(
                SecurityRequirement.class
            );

        assertThat(security)
            .isNotNull();

        assertThat(security.name())
            .isEqualTo(
                OpenApiConfiguration
                    .BEARER_AUTH_SCHEME
            );

        Parameter jwtParameter =
            method
                .getParameters()[0]
                .getAnnotation(Parameter.class);

        assertThat(jwtParameter)
            .isNotNull();

        assertThat(jwtParameter.hidden())
            .isTrue();

        ApiResponses responses =
            method.getAnnotation(ApiResponses.class);

        assertThat(responses)
            .isNotNull();

        assertThat(
            Arrays.stream(responses.value())
                .map(ApiResponse::responseCode)
                .toList()
        )
            .containsExactlyInAnyOrder(
                expectedResponseCodes
            );

        ApiResponse unauthorizedResponse =
            Arrays.stream(responses.value())
                .filter(response ->
                    response
                        .responseCode()
                        .equals("401")
                )
                .findFirst()
                .orElseThrow();

        assertThat(unauthorizedResponse.content())
            .isEmpty();
    }
    @Test
    void shouldDocumentMoneyTransfer()
        throws NoSuchMethodException {

        Method method =
            TransferMoneyController.class
                .getDeclaredMethod(
                    "transfer",
                    Jwt.class,
                    String.class,
                    TransferMoneyRequest.class
                );

        assertAuthenticatedDocumentation(
            TransferMoneyController.class,
            method,
            "Transfers",
            "transferMoney",
            "201",
            "400",
            "401",
            "404",
            "409",
            "422"
        );

        Parameter headerDocumentation =
            method
                .getParameters()[1]
                .getAnnotation(Parameter.class);

        assertThat(headerDocumentation)
            .isNotNull();

        assertThat(headerDocumentation.name())
            .isEqualTo("Idempotency-Key");

        assertThat(headerDocumentation.required())
            .isTrue();

        RequestHeader requestHeader =
            method
                .getParameters()[1]
                .getAnnotation(RequestHeader.class);

        assertThat(requestHeader)
            .isNotNull();

        assertThat(requestHeader.value())
            .isEqualTo("Idempotency-Key");
    }

    @Test
    void shouldDocumentTransactionHistory()
        throws NoSuchMethodException {

        Method method =
            GetTransactionHistoryController.class
                .getDeclaredMethod(
                    "getTransactionHistory",
                    Jwt.class,
                    int.class,
                    int.class,
                    TransactionDirection.class,
                    TransactionStatus.class,
                    Instant.class,
                    Instant.class
                );

        assertAuthenticatedDocumentation(
            GetTransactionHistoryController.class,
            method,
            "Transactions",
            "getTransactionHistory",
            "200",
            "400",
            "401",
            "404"
        );

        assertDocumentedParameter(
            method,
            1,
            "page"
        );

        assertDocumentedParameter(
            method,
            2,
            "size"
        );

        assertDocumentedParameter(
            method,
            3,
            "direction"
        );

        assertDocumentedParameter(
            method,
            4,
            "status"
        );

        assertDocumentedParameter(
            method,
            5,
            "from"
        );

        assertDocumentedParameter(
            method,
            6,
            "to"
        );

        Parameter fromParameter =
            method
                .getParameters()[5]
                .getAnnotation(Parameter.class);

        assertThat(fromParameter.description())
            .containsIgnoringCase("inclusive");

        Parameter toParameter =
            method
                .getParameters()[6]
                .getAnnotation(Parameter.class);

        assertThat(toParameter.description())
            .containsIgnoringCase("exclusive");
    }

    private static void assertDocumentedParameter(
        Method method,
        int parameterIndex,
        String expectedName
    ) {
        Parameter parameter =
            method
                .getParameters()[parameterIndex]
                .getAnnotation(Parameter.class);

        assertThat(parameter)
            .isNotNull();

        assertThat(parameter.name())
            .isEqualTo(expectedName);

        assertThat(parameter.description())
            .isNotBlank();
    }

}
