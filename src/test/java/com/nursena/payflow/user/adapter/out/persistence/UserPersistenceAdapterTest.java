package com.nursena.payflow.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;

import com.nursena.payflow.user.domain.exception.EmailAlreadyRegisteredException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserPersistenceAdapterTest {

    private static final Instant NOW =
        Instant.parse("2026-07-12T12:00:00Z");

    @Mock
    private SpringDataUserRepository repository;

    private UserPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserPersistenceAdapter(repository);
    }

    @Test
    void shouldCheckExistenceUsingNormalizedEmail() {
        EmailAddress email = EmailAddress.of("NURSENA@example.com");

        when(repository.existsByEmail("nursena@example.com"))
            .thenReturn(true);

        boolean exists = adapter.existsByEmail(email);

        assertThat(exists).isTrue();
        verify(repository).existsByEmail("nursena@example.com");
    }

    @Test
    void shouldSaveAndRestoreUser() {
        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            NOW
        );

        when(repository.saveAndFlush(any(UserJpaEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        User savedUser = adapter.save(user);

        assertThat(savedUser.id()).isEqualTo(user.id());
        assertThat(savedUser.email()).isEqualTo(user.email());
        assertThat(savedUser.passwordHash())
            .isEqualTo(user.passwordHash());
        assertThat(savedUser.role()).isEqualTo(user.role());
        assertThat(savedUser.status()).isEqualTo(user.status());
        assertThat(savedUser.createdAt()).isEqualTo(NOW);
        assertThat(savedUser.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldTranslateDuplicateEmailConstraintViolation() {
        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            NOW
        );

        ConstraintViolationException constraintViolation =
            new ConstraintViolationException(
                "duplicate email",
                new SQLException(),
                "uq_users_email_lower"
            );

        when(repository.saveAndFlush(any(UserJpaEntity.class)))
            .thenThrow(new DataIntegrityViolationException(
                "duplicate email",
                constraintViolation
            ));

        assertThatThrownBy(() -> adapter.save(user))
            .isInstanceOf(EmailAlreadyRegisteredException.class)
            .hasMessage(
                "A user with this email address already exists."
            );
    }
}
