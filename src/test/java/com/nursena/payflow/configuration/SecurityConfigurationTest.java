package com.nursena.payflow.configuration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
    controllers =
        SecurityConfigurationTest.TestController.class
)
@Import(SecurityConfiguration.class)
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

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

    @RestController
    static class TestController {
    }
}
