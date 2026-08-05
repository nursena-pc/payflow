package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.nursena.payflow.user.application.port.in
    .RegisterUserCommand;
import com.nursena.payflow.user.application.port.in
    .RegisterUserUseCase;
import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialRepositoryPort;
import com.nursena.payflow.user.domain.model
    .AccountActionCredential;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialDigest;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Import(
    RegisterUserEmailVerificationRollbackIntegrationTest
        .FailureInjectionConfiguration.class
)
class RegisterUserEmailVerificationRollbackIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private RegisterUserUseCase registerUser;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FailureInjectingCredentialRepository
        credentialRepository;

    @BeforeEach
    void cleanDatabase() {
        credentialRepository.reset();
        jdbcTemplate.update(
            "DELETE FROM account_action_credentials"
        );
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldRollbackUserWhenVerificationIssuanceFails() {
        credentialRepository.failNextSave();

        assertThatThrownBy(() ->
            registerUser.register(
                new RegisterUserCommand(
                    "rollback@example.com",
                    "StrongPassword123!"
                )
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "forced credential persistence failure"
            );

        assertThat(count("users")).isZero();
        assertThat(count("account_action_credentials"))
            .isZero();
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table,
            Integer.class
        );

        return count == null ? 0 : count;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureInjectionConfiguration {

        @Bean
        @Primary
        FailureInjectingCredentialRepository
        failureInjectingCredentialRepository(
            @Qualifier(
                "accountActionCredentialPersistenceAdapter"
            )
            AccountActionCredentialRepositoryPort delegate
        ) {
            return new FailureInjectingCredentialRepository(
                delegate
            );
        }
    }

    static final class FailureInjectingCredentialRepository
        implements AccountActionCredentialRepositoryPort {

        private final AccountActionCredentialRepositoryPort
            delegate;

        private final AtomicBoolean failNextSave =
            new AtomicBoolean();

        FailureInjectingCredentialRepository(
            AccountActionCredentialRepositoryPort delegate
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
        public AccountActionCredential save(
            AccountActionCredential credential
        ) {
            if (failNextSave.compareAndSet(true, false)) {
                throw new IllegalStateException(
                    "forced credential persistence failure"
                );
            }

            return delegate.save(credential);
        }

        @Override
        public int supersedeUnresolved(
            UUID userId,
            AccountActionCredentialPurpose purpose,
            Instant supersededAt
        ) {
            return delegate.supersedeUnresolved(
                userId,
                purpose,
                supersededAt
            );
        }

        @Override
        public Optional<UUID>
        findUserIdByDigestAndPurpose(
            AccountActionCredentialDigest digest,
            AccountActionCredentialPurpose purpose
        ) {
            return delegate.findUserIdByDigestAndPurpose(
                digest,
                purpose
            );
        }

        @Override
        public Optional<AccountActionCredential>
        findByDigestAndPurposeForUpdate(
            AccountActionCredentialDigest digest,
            AccountActionCredentialPurpose purpose
        ) {
            return delegate.findByDigestAndPurposeForUpdate(
                digest,
                purpose
            );
        }
    }
}
