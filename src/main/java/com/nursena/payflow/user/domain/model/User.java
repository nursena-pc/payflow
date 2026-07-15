package com.nursena.payflow.user.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class User {

    private final UUID id;
    private final EmailAddress email;
    private final String passwordHash;
    private final UserRole role;
    private UserStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(
        UUID id,
        EmailAddress email,
        String passwordHash,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = requirePasswordHash(passwordHash);
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static User register(
        EmailAddress email,
        String passwordHash,
        Instant now
    ) {
        return new User(
            UUID.randomUUID(),
            email,
            passwordHash,
            UserRole.USER,
            UserStatus.ACTIVE,
            now,
            now
        );
    }

    public static User rehydrate(
        UUID id,
        EmailAddress email,
        String passwordHash,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new User(
            id,
            email,
            passwordHash,
            role,
            status,
            createdAt,
            updatedAt
        );
    }

    public void suspend(Instant now) {
        status = UserStatus.SUSPENDED;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static String requirePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }

        return passwordHash;
    }

    public UUID id() {
        return id;
    }

    public EmailAddress email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UserRole role() {
        return role;
    }

    public UserStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
