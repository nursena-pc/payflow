
package com.nursena.payflow.user.adapter.in.web;

import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationConfiguration;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import com.nursena.payflow.clientcontext.adapter.in.web
    .ClientAddressResolver;
import com.nursena.payflow.clientcontext.domain
    .ClientAddressResolutionOutcome;
import com.nursena.payflow.clientcontext.domain
    .ClientAddressSource;
import com.nursena.payflow.clientcontext.domain.IpAddress;
import com.nursena.payflow.clientcontext.domain
    .ResolvedClientAddress;

import com.nursena.payflow.configuration
    .SecurityConfiguration;
import com.nursena.payflow.user.application.exception
    .LoginRateLimitExceededException;
import com.nursena.payflow.user.application.exception
    .LoginRateLimitUnavailableException;
import com.nursena.payflow.user.application.port.in
    .AuthenticateUserCommand;
import com.nursena.payflow.user.application.port.in
    .AuthenticatedUserResult;
import com.nursena.payflow.user.application.port.in
    .MfaChallengeRequiredResult;
import com.nursena.payflow.user.application.port.in
    .AuthenticateUserUseCase;
import com.nursena.payflow.user.application.port.out
    .LoginRateLimitDimension;
import com.nursena.payflow.user.domain.exception
    .InvalidCredentialsException;
import com.nursena.payflow.user.domain.exception
    .UserAccountUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation
    .Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
    .WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito
    .MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthenticateUserController.class)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    UserAuthenticationExceptionHandler.class
})
class AuthenticateUserControllerTest {

    private static final String DIRECT_PEER_ADDRESS =
        "10.0.0.10";

    private static final String CLIENT_ADDRESS =
        "203.0.113.10";

    private static final Instant ACCESS_EXPIRES_AT =
        Instant.parse(
            "2026-07-28T12:15:00Z"
        );

    private static final Instant REFRESH_EXPIRES_AT =
        Instant.parse(
            "2026-08-04T12:00:00Z"
        );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticateUserUseCase
        authenticateUserUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientAddressResolver
        clientAddressResolver;

    @BeforeEach
    void resolveEffectiveClientAddress() {
        when(clientAddressResolver.resolve(
            any(HttpServletRequest.class)
        ))
            .thenReturn(
                new ResolvedClientAddress(
                    IpAddress.parse(
                        CLIENT_ADDRESS
                    ),
                    ClientAddressSource.FORWARDED,
                    ClientAddressResolutionOutcome
                        .RESOLVED
                )
            );
    }

    @Test
    void shouldAuthenticateUserAndReturnCredentialPair()
        throws Exception {

        when(authenticateUserUseCase.authenticate(
            any(AuthenticateUserCommand.class)
        ))
            .thenReturn(
                new AuthenticatedUserResult(
                    "signed-access-token",
                    ACCESS_EXPIRES_AT,
                    "opaque-refresh-token",
                    REFRESH_EXPIRES_AT
                )
            );

        mockMvc.perform(
                loginRequest(
                    "nursena@example.com",
                    "StrongPassword123!"
                )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$.accessToken"
                ).value(
                    "signed-access-token"
                )
            )
            .andExpect(
                jsonPath(
                    "$.tokenType"
                ).value("Bearer")
            )
            .andExpect(
                jsonPath(
                    "$.expiresAt"
                ).value(
                    ACCESS_EXPIRES_AT.toString()
                )
            )
            .andExpect(
                jsonPath(
                    "$.refreshToken"
                ).value(
                    "opaque-refresh-token"
                )
            )
            .andExpect(
                jsonPath(
                    "$.refreshTokenExpiresAt"
                ).value(
                    REFRESH_EXPIRES_AT.toString()
                )
            )
            .andExpect(
                jsonPath(
                    "$.tokenDigest"
                ).doesNotExist()
            )
            .andExpect(
                jsonPath(
                    "$.familyId"
                ).doesNotExist()
            );

        verify(authenticateUserUseCase)
            .authenticate(
                argThat(command ->
                    command.email().equals(
                        "nursena@example.com"
                    )
                        && command.rawPassword()
                            .equals(
                                "StrongPassword123!"
                            )
                        && command.clientAddress()
                            .equals(
                                CLIENT_ADDRESS
                            )
                )
            );
    }

    @Test
    void shouldReturnAcceptedChallengeWithoutCredentialFieldsWhenMfaIsEnabled()
        throws Exception {

        when(authenticateUserUseCase.authenticate(
            any(AuthenticateUserCommand.class)
        ))
            .thenReturn(
                new MfaChallengeRequiredResult(
                    "opaque-mfa-challenge",
                    Instant.parse("2026-07-28T12:05:00Z")
                )
            );

        mockMvc.perform(
                loginRequest(
                    "nursena@example.com",
                    "StrongPassword123!"
                )
            )
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.authenticationStatus").value("MFA_REQUIRED"))
            .andExpect(jsonPath("$.challengeToken").value("opaque-mfa-challenge"))
            .andExpect(jsonPath("$.expiresAt").value("2026-07-28T12:05:00Z"))
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void shouldRedactCredentialValuesFromResponseToString() {
        AuthenticateUserResponse response =
            new AuthenticateUserResponse(
                "secret-access-token",
                "Bearer",
                ACCESS_EXPIRES_AT,
                "secret-refresh-token",
                REFRESH_EXPIRES_AT
            );

        assertThat(response.toString())
            .isEqualTo(
                "AuthenticateUserResponse[redacted]"
            )
            .doesNotContain(
                "secret-access-token",
                "secret-refresh-token"
            );
    }

    @Test
    void shouldRejectInvalidEmail()
        throws Exception {

        mockMvc.perform(
                loginRequest(
                    "not-an-email",
                    "StrongPassword123!"
                )
            )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath(
                    "$.code"
                ).value(
                    "VALIDATION_FAILED"
                )
            )
            .andExpect(
                jsonPath(
                    "$.violations[0].field"
                ).value("email")
            );
    }

    @Test
    void shouldReturnUnauthorizedForInvalidCredentials()
        throws Exception {

        when(authenticateUserUseCase.authenticate(
            any(AuthenticateUserCommand.class)
        ))
            .thenThrow(
                new InvalidCredentialsException()
            );

        mockMvc.perform(
                loginRequest(
                    "nursena@example.com",
                    "WrongPassword123!"
                )
            )
            .andExpect(
                status().isUnauthorized()
            )
            .andExpect(
                jsonPath(
                    "$.code"
                ).value(
                    "INVALID_CREDENTIALS"
                )
            )
            .andExpect(
                jsonPath(
                    "$.message"
                ).value(
                    "Email or password is incorrect."
                )
            )
            .andExpect(
                jsonPath(
                    "$.path"
                ).value(
                    "/api/v1/auth/login"
                )
            );
    }

    @Test
    void shouldReturnForbiddenForUnavailableAccount()
        throws Exception {

        when(authenticateUserUseCase.authenticate(
            any(AuthenticateUserCommand.class)
        ))
            .thenThrow(
                new UserAccountUnavailableException()
            );

        mockMvc.perform(
                loginRequest(
                    "nursena@example.com",
                    "StrongPassword123!"
                )
            )
            .andExpect(
                status().isForbidden()
            )
            .andExpect(
                jsonPath(
                    "$.code"
                ).value(
                    "USER_ACCOUNT_UNAVAILABLE"
                )
            )
            .andExpect(
                jsonPath(
                    "$.message"
                ).value(
                    "User account is not available "
                        + "for authentication."
                )
            )
            .andExpect(
                jsonPath(
                    "$.path"
                ).value(
                    "/api/v1/auth/login"
                )
            );
    }

    @Test
    void shouldReturnTooManyRequestsWithRetryAfter()
        throws Exception {

        when(authenticateUserUseCase.authenticate(
            any(AuthenticateUserCommand.class)
        ))
            .thenThrow(
                new LoginRateLimitExceededException(
                    LoginRateLimitDimension.IDENTITY,
                    Duration.ofSeconds(420)
                )
            );

        mockMvc.perform(
                loginRequest(
                    "nursena@example.com",
                    "WrongPassword123!"
                )
            )
            .andExpect(
                status().isTooManyRequests()
            )
            .andExpect(
                header().string(
                    HttpHeaders.RETRY_AFTER,
                    "420"
                )
            )
            .andExpect(
                jsonPath("$.status")
                    .value(429)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "LOGIN_RATE_LIMIT_EXCEEDED"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Too many login attempts. "
                            + "Try again later."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        "/api/v1/auth/login"
                    )
            )
            .andExpect(
                jsonPath("$.violations")
                    .isEmpty()
            );
    }

    @Test
    void shouldFailClosedWhenLoginProtectionIsUnavailable()
        throws Exception {

        when(authenticateUserUseCase.authenticate(
            any(AuthenticateUserCommand.class)
        ))
            .thenThrow(
                new LoginRateLimitUnavailableException(
                    new IllegalStateException(
                        "redis-host:6379 unavailable"
                    )
                )
            );

        mockMvc.perform(
                loginRequest(
                    "nursena@example.com",
                    "StrongPassword123!"
                )
            )
            .andExpect(
                status().isServiceUnavailable()
            )
            .andExpect(
                jsonPath("$.status")
                    .value(503)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "LOGIN_RATE_LIMIT_UNAVAILABLE"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Login protection is "
                            + "temporarily unavailable."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        "/api/v1/auth/login"
                    )
            )
            .andExpect(
                jsonPath("$.violations")
                    .isEmpty()
            );
    }

    private static org.springframework.test.web.servlet
        .request.MockHttpServletRequestBuilder
    loginRequest(
        String email,
        String password
    ) {
        String body =
            """
            {
              "email": "%s",
              "password": "%s"
            }
            """.formatted(
                email,
                password
            );

        return post("/api/v1/auth/login")
            .with(request -> {
                request.setRemoteAddr(
                    DIRECT_PEER_ADDRESS
                );
                return request;
            })
            .contentType(
                MediaType.APPLICATION_JSON
            )
            .content(body);
    }
}
