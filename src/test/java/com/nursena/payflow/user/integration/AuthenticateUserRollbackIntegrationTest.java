package com.nursena.payflow.user.integration;

import static com.nursena.payflow.user.support.EmailVerificationTestSupport.markVerified;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.user.application.port.out.AccessTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.GeneratedAccessToken;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.RefreshTokenRecordId;
import com.nursena.payflow.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
        "payflow.security.refresh-session.refresh-token-ttl=7d",
        "payflow.security.refresh-session.family-ttl=30d"
    }
)
@AutoConfigureMockMvc
@Testcontainers
@Import(
    AuthenticateUserRollbackIntegrationTest
        .FailureInjectionConfiguration.class
)
class AuthenticateUserRollbackIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FailureInjectingRecordRepository
        recordRepository;

    @Autowired
    private FailureInjectingAccessTokenGeneration
        accessTokenGeneration;

    @BeforeEach
    void setUp() {
        recordRepository.reset();
        accessTokenGeneration.reset();

        jdbcTemplate.update(
            "DELETE FROM refresh_token_records"
        );

        jdbcTemplate.update(
            "DELETE FROM refresh_token_families"
        );

        jdbcTemplate.update(
            "DELETE FROM users"
        );
    }

    @Test
    void shouldRollbackFamilyWhenInitialRecordPersistenceFails()
        throws Exception {

        Credentials credentials =
            registerUniqueUser(
                "record-failure"
            );

        recordRepository.failNextSave();

        assertThatThrownBy(() ->
            authenticate(
                credentials
            )
        )
            .isInstanceOf(
                jakarta.servlet.ServletException.class
            )
            .hasRootCauseInstanceOf(
                IllegalStateException.class
            )
            .hasRootCauseMessage(
                "initial refresh-token record persistence failed"
            );

        assertRefreshSessionTablesAreEmpty();
    }

    @Test
    void shouldRollbackFamilyAndRecordWhenAccessTokenGenerationFails()
        throws Exception {

        Credentials credentials =
            registerUniqueUser(
                "access-failure"
            );

        accessTokenGeneration
            .failNextGeneration();

        assertThatThrownBy(() ->
            authenticate(
                credentials
            )
        )
            .isInstanceOf(
                jakarta.servlet.ServletException.class
            )
            .hasRootCauseInstanceOf(
                IllegalStateException.class
            )
            .hasRootCauseMessage(
                "access-token generation failed"
            );

        assertRefreshSessionTablesAreEmpty();
    }

    private Credentials registerUniqueUser(
        String prefix
    ) throws Exception {

        Credentials credentials =
            new Credentials(
                prefix
                    + "-"
                    + UUID.randomUUID()
                    + "@example.com",
                "StrongPassword123!"
            );

        mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            credentials
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            );

        markVerified(jdbcTemplate, credentials.email());

        return credentials;
    }

    private org.springframework.test.web.servlet
    .ResultActions authenticate(
        Credentials credentials
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    objectMapper.writeValueAsString(
                        credentials
                    )
                )
        );
    }

    private void assertRefreshSessionTablesAreEmpty() {
        Integer familyCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_families
                """,
                Integer.class
            );

        Integer recordCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_records
                """,
                Integer.class
            );

        assertThat(familyCount)
            .isZero();

        assertThat(recordCount)
            .isZero();
    }

    private record Credentials(
        String email,
        String password
    ) {
    }

    @TestConfiguration(
        proxyBeanMethods = false
    )
    static class FailureInjectionConfiguration {

        @Bean
        @Primary
        FailureInjectingRecordRepository
        failureInjectingRecordRepository(
            @Qualifier(
                "refreshTokenRecordPersistenceAdapter"
            )
            RefreshTokenRecordRepositoryPort delegate
        ) {
            return new FailureInjectingRecordRepository(
                delegate
            );
        }

        @Bean
        @Primary
        FailureInjectingAccessTokenGeneration
        failureInjectingAccessTokenGeneration(
            @Qualifier(
                "jwtAccessTokenGenerationAdapter"
            )
            AccessTokenGenerationPort delegate
        ) {
            return new FailureInjectingAccessTokenGeneration(
                delegate
            );
        }
    }

    static final class FailureInjectingRecordRepository
        implements RefreshTokenRecordRepositoryPort {

        private final RefreshTokenRecordRepositoryPort
            delegate;

        private final AtomicBoolean failNextSave =
            new AtomicBoolean();

        FailureInjectingRecordRepository(
            RefreshTokenRecordRepositoryPort delegate
        ) {
            this.delegate = delegate;
        }

        void failNextSave() {
            failNextSave.set(true);
        }

        void reset() {
            failNextSave.set(false);
        }

        @Override
        public RefreshTokenRecord save(
            RefreshTokenRecord record
        ) {
            if (
                failNextSave.compareAndSet(
                    true,
                    false
                )
            ) {
                throw new IllegalStateException(
                    "initial refresh-token "
                        + "record persistence failed"
                );
            }

            return delegate.save(record);
        }

        @Override
        public Optional<RefreshTokenRecord>
        findByDigestForUpdate(
            RefreshTokenDigest digest
        ) {
            return delegate
                .findByDigestForUpdate(
                    digest
                );
        }

        @Override
        public Optional<RefreshTokenRecord>
        findById(
            RefreshTokenRecordId recordId
        ) {
            return delegate.findById(
                recordId
            );
        }
    }

    static final class FailureInjectingAccessTokenGeneration
        implements AccessTokenGenerationPort {

        private final AccessTokenGenerationPort
            delegate;

        private final AtomicBoolean
            failNextGeneration =
            new AtomicBoolean();

        FailureInjectingAccessTokenGeneration(
            AccessTokenGenerationPort delegate
        ) {
            this.delegate = delegate;
        }

        void failNextGeneration() {
            failNextGeneration.set(true);
        }

        void reset() {
            failNextGeneration.set(false);
        }

        @Override
        public GeneratedAccessToken generate(
            User user
        ) {
            if (
                failNextGeneration.compareAndSet(
                    true,
                    false
                )
            ) {
                throw new IllegalStateException(
                    "access-token generation failed"
                );
            }

            return delegate.generate(user);
        }
    }
}
