package com.nursena.payflow.user.adapter.in.web;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.user.application.port.in.RevokeAllRefreshSessionsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    RevokeAllRefreshSessionsController.class
)
@Import(SecurityConfiguration.class)
class RevokeAllRefreshSessionsControllerTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "95000000-0000-0000-0000-000000000003"
        );

    private static final UUID REQUEST_USER_ID =
        UUID.fromString(
            "95000000-0000-0000-0000-000000000004"
        );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RevokeAllRefreshSessionsUseCase
        revokeAllRefreshSessionsUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldRevokeAllSessionsForAuthenticatedSubject()
        throws Exception {

        mockMvc.perform(
                authenticatedLogoutAllRequest()
            )
            .andExpect(
                status().isNoContent()
            )
            .andExpect(
                content().string("")
            );

        verify(
            revokeAllRefreshSessionsUseCase
        ).revoke(
            argThat(command ->
                USER_ID.equals(
                    command.userId()
                )
            )
        );
    }

    @Test
    void shouldRejectAnonymousRequest()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/auth/logout-all"
                )
            )
            .andExpect(
                status().isUnauthorized()
            );

        verifyNoInteractions(
            revokeAllRefreshSessionsUseCase
        );
    }

    @Test
    void shouldIgnoreRequestControlledUserId()
        throws Exception {

        mockMvc.perform(
                authenticatedLogoutAllRequest()
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "userId": "%s"
                        }
                        """
                            .formatted(
                                REQUEST_USER_ID
                            )
                    )
            )
            .andExpect(
                status().isNoContent()
            );

        verify(
            revokeAllRefreshSessionsUseCase
        ).revoke(
            argThat(command ->
                USER_ID.equals(
                    command.userId()
                )
                    && !REQUEST_USER_ID.equals(
                        command.userId()
                    )
            )
        );
    }

    private static org.springframework.test.web
    .servlet.request.MockHttpServletRequestBuilder
    authenticatedLogoutAllRequest() {
        return post(
            "/api/v1/auth/logout-all"
        )
            .with(
                jwt().jwt(token ->
                    token.subject(
                        USER_ID.toString()
                    )
                )
            );
    }
}
