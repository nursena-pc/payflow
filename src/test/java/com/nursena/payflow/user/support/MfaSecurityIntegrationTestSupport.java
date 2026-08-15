package com.nursena.payflow.user.support;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.nursena.payflow.user.application.port.out.MfaSecretProtectionPort;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class MfaSecurityIntegrationTestSupport {

    private MfaSecurityIntegrationTestSupport() {
    }

    public static MfaUserFixture insertEnabledMfaUser(
        JdbcTemplate jdbcTemplate,
        PasswordEncoder passwordEncoder,
        MfaSecretProtectionPort secretProtection,
        String password,
        byte[] totpSecret
    ) {
        UUID userId = UUID.randomUUID();
        String email = userId + "@example.com";
        Instant now = Instant.now();

        jdbcTemplate.update(
            """
            INSERT INTO users (
                id,
                email,
                password_hash,
                role,
                status,
                email_verified_at,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
            """,
            userId,
            email,
            passwordEncoder.encode(password),
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );

        ProtectedMfaSecret protectedSecret =
            secretProtection.protect(
                userId,
                Arrays.copyOf(
                    totpSecret,
                    totpSecret.length
                )
            );

        jdbcTemplate.update(
            """
            INSERT INTO mfa_authenticators (
                user_id,
                state,
                protected_secret,
                enrollment_expires_at,
                activated_at,
                created_at,
                updated_at
            ) VALUES (?, 'ENABLED', ?, NULL, ?, ?, ?)
            """,
            userId,
            protectedSecret.value(),
            Timestamp.from(now),
            Timestamp.from(now.minusSeconds(60)),
            Timestamp.from(now)
        );

        return new MfaUserFixture(userId, email);
    }

    public static void insertRecoveryCode(
        JdbcTemplate jdbcTemplate,
        UUID userId,
        String recoveryCode
    ) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(
                recoveryCode.getBytes(
                    StandardCharsets.US_ASCII
                )
            );

        jdbcTemplate.update(
            """
            INSERT INTO mfa_recovery_codes (
                id,
                user_id,
                code_digest,
                created_at,
                consumed_at
            ) VALUES (?, ?, ?, ?, NULL)
            """,
            UUID.randomUUID(),
            userId,
            digest,
            Timestamp.from(Instant.now())
        );
    }

    public static String currentTotp(
        byte[] secret
    ) throws Exception {
        long counter = Math.floorDiv(
            Instant.now().getEpochSecond(),
            30L
        );

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(
            new SecretKeySpec(
                secret,
                "HmacSHA1"
            )
        );

        byte[] digest = mac.doFinal(
            ByteBuffer.allocate(Long.BYTES)
                .putLong(counter)
                .array()
        );

        int offset = digest[digest.length - 1] & 0x0f;

        int binary =
            ((digest[offset] & 0x7f) << 24)
                | ((digest[offset + 1] & 0xff) << 16)
                | ((digest[offset + 2] & 0xff) << 8)
                | (digest[offset + 3] & 0xff);

        return String.format(
            "%06d",
            binary % 1_000_000
        );
    }

    public static String differentTotp(
        byte[] secret
    ) throws Exception {
        String current = currentTotp(secret);

        return current.substring(0, 5)
            + (current.endsWith("0") ? "1" : "0");
    }

    public record MfaUserFixture(
        UUID userId,
        String email
    ) {
    }
}