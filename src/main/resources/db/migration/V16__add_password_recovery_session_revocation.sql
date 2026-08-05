ALTER TABLE refresh_token_families
    DROP CONSTRAINT chk_refresh_token_families_revocation_reason;

ALTER TABLE refresh_token_families
    ADD CONSTRAINT chk_refresh_token_families_revocation_reason
    CHECK (
        revocation_reason IS NULL
        OR revocation_reason IN (
            'CURRENT_SESSION_LOGOUT',
            'ALL_SESSIONS_LOGOUT',
            'REUSE_DETECTED',
            'USER_ACCOUNT_UNAVAILABLE',
            'PASSWORD_RECOVERY',
            'ADMINISTRATIVE_REVOCATION'
        )
    );
