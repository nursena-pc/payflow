package com.nursena.payflow.user.support;

import com.nursena.payflow.user.domain.model.EmailAddress;
import org.springframework.jdbc.core.JdbcTemplate;

public final class EmailVerificationTestSupport {

    private EmailVerificationTestSupport() {
    }

    public static void markVerified(
        JdbcTemplate jdbcTemplate,
        String email
    ) {
        int updated = jdbcTemplate.update(
            """
            UPDATE users
            SET email_verified_at = COALESCE(
                    email_verified_at,
                    CURRENT_TIMESTAMP
                ),
                updated_at = GREATEST(
                    updated_at,
                    CURRENT_TIMESTAMP
                )
            WHERE email = ?
            """,
            EmailAddress.of(email).value()
        );

        if (updated != 1) {
            throw new IllegalStateException(
                "Expected one registered user to be verified."
            );
        }
    }
}
