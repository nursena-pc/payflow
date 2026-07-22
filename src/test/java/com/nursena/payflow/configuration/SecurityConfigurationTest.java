package com.nursena.payflow.configuration;

import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;

import java.time.Instant;

import com.nursena.payflowtest.configuration.SecurityProbeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
    .WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito
    .MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebMvcTest
@ContextConfiguration(
    classes = {
        SecurityConfiguration.class,
        SecurityProbeController.class
    }
)
class SecurityConfigurationTest {

    private static final Instant ISSUED_AT =
        Instant.parse("2026-07-22T12:00:00Z");

    private static final Instant EXPIRES_AT =
        Instant.parse("2026-07-22T12:15:00Z");

    private static final String SUBJECT =
        "10000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private SecurityProbeController.ProbeService probeService;
    @Test
    void shouldPermitAnonymousRequestToPrometheusEndpoint()
        throws Exception {

        mockMvc.perform(
                get("/actuator/prometheus")
            )
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldPermitAnonymousRequestToHealthSubEndpoints()
        throws Exception {

        mockMvc.perform(
                get("/actuator/health/liveness")
            )
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectAnonymousRequestToSensitiveActuatorEndpoints()
        throws Exception {

        mockMvc.perform(
                get("/actuator/env")
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAnonymousOperationsRequest()
        throws Exception {

        mockMvc.perform(
                get("/api/v1/operations/probe")
            )
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(probeService);
    }

    @Test
    void shouldRejectUserWithoutOperationsAuthority()
        throws Exception {

        mockDecodedJwt(
            "user-token",
            "USER"
        );

        mockMvc.perform(
                get("/api/v1/operations/probe")
                    .header(
                        AUTHORIZATION,
                        bearer("user-token")
                    )
            )
            .andExpect(status().isForbidden());
        verifyNoInteractions(probeService);
    }

    @Test
    void shouldRejectTokenWithoutRoleClaim()
        throws Exception {

        when(
            jwtDecoder.decode("missing-role-token")
        ).thenReturn(
            jwtWithoutRole("missing-role-token")
        );

        mockMvc.perform(
                get("/api/v1/operations/probe")
                    .header(
                        AUTHORIZATION,
                        bearer("missing-role-token")
                    )
            )
            .andExpect(status().isForbidden());
        verifyNoInteractions(probeService);
    }

    @Test
    void shouldPermitAdminOperationsRequest()
        throws Exception {

        mockDecodedJwt(
            "admin-token",
            "ADMIN"
        );

        mockMvc.perform(
                get("/api/v1/operations/probe")
                    .header(
                        AUTHORIZATION,
                        bearer("admin-token")
                    )
            )
            .andExpect(status().isOk());

        verify(
            probeService
        ).operationsAccessed();
    }

    @Test
    void shouldIgnoreRequestControlledRoleHeader()
        throws Exception {

        mockDecodedJwt(
            "user-token",
            "USER"
        );

        mockMvc.perform(
                get("/api/v1/operations/probe")
                    .header(
                        AUTHORIZATION,
                        bearer("user-token")
                    )
                    .header(
                        "X-PayFlow-Role",
                        "ADMIN"
                    )
            )
            .andExpect(status().isForbidden());
        verifyNoInteractions(probeService);
    }

    @Test
    void shouldPreserveAuthenticatedCustomerAccess()
        throws Exception {

        mockDecodedJwt(
            "user-token",
            "USER"
        );

        mockMvc.perform(
                get("/api/v1/users/me")
                    .header(
                        AUTHORIZATION,
                        bearer("user-token")
                    )
            )
            .andExpect(status().isOk());

        verify(
            probeService
        ).customerAccessed();
    }

    @Test
    void shouldDenyUnknownEndpointForAuthenticatedAdmin()
        throws Exception {

        mockDecodedJwt(
            "admin-token",
            "ADMIN"
        );

        mockMvc.perform(
                get("/api/v1/internal/probe")
                    .header(
                        AUTHORIZATION,
                        bearer("admin-token")
                    )
            )
            .andExpect(status().isForbidden());
        verifyNoInteractions(probeService);
    }


    private void mockDecodedJwt(
        String tokenValue,
        String role
    ) {
        when(
            jwtDecoder.decode(tokenValue)
        ).thenReturn(
            jwtWithRole(
                tokenValue,
                role
            )
        );
    }

    private static Jwt jwtWithRole(
        String tokenValue,
        String role
    ) {
        return baseJwt(tokenValue)
            .claim("role", role)
            .build();
    }

    private static Jwt jwtWithoutRole(
        String tokenValue
    ) {
        return baseJwt(tokenValue)
            .build();
    }

    private static Jwt.Builder baseJwt(
        String tokenValue
    ) {
        return Jwt
            .withTokenValue(tokenValue)
            .header("alg", "RS256")
            .subject(SUBJECT)
            .issuedAt(ISSUED_AT)
            .expiresAt(EXPIRES_AT);
    }

    private static String bearer(
        String tokenValue
    ) {
        return "Bearer " + tokenValue;
    }

}
