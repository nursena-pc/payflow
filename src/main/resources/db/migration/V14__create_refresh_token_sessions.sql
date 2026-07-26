CREATE TABLE refresh_token_families (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(40),

    CONSTRAINT fk_refresh_token_families_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_refresh_token_families_lifetime
        CHECK (
            expires_at > created_at
        ),

    CONSTRAINT chk_refresh_token_families_revocation_state
        CHECK (
            (
                revoked_at IS NULL
                AND revocation_reason IS NULL
            )
            OR
            (
                revoked_at IS NOT NULL
                AND revocation_reason IS NOT NULL
            )
        ),

    CONSTRAINT chk_refresh_token_families_revocation_time
        CHECK (
            revoked_at IS NULL
            OR revoked_at >= created_at
        ),

    CONSTRAINT chk_refresh_token_families_revocation_reason
        CHECK (
            revocation_reason IS NULL
            OR revocation_reason IN (
                'CURRENT_SESSION_LOGOUT',
                'ALL_SESSIONS_LOGOUT',
                'REUSE_DETECTED',
                'USER_ACCOUNT_UNAVAILABLE',
                'ADMINISTRATIVE_REVOCATION'
            )
        )
);

CREATE TABLE refresh_token_records (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    token_digest BYTEA NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    successor_id UUID,

    CONSTRAINT uq_refresh_token_records_id_family
        UNIQUE (
            id,
            family_id
        ),

    CONSTRAINT uq_refresh_token_records_digest
        UNIQUE (
            token_digest
        ),

    CONSTRAINT uq_refresh_token_records_successor
        UNIQUE (
            successor_id
        ),

    CONSTRAINT fk_refresh_token_records_family
        FOREIGN KEY (family_id)
        REFERENCES refresh_token_families(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_refresh_token_records_digest_length
        CHECK (
            octet_length(token_digest) = 32
        ),

    CONSTRAINT chk_refresh_token_records_lifetime
        CHECK (
            expires_at > issued_at
        ),

    CONSTRAINT chk_refresh_token_records_consumption_state
        CHECK (
            (
                consumed_at IS NULL
                AND successor_id IS NULL
            )
            OR
            (
                consumed_at IS NOT NULL
                AND successor_id IS NOT NULL
            )
        ),

    CONSTRAINT chk_refresh_token_records_consumption_time
        CHECK (
            consumed_at IS NULL
            OR (
                consumed_at >= issued_at
                AND consumed_at < expires_at
            )
        ),

    CONSTRAINT chk_refresh_token_records_not_self_successor
        CHECK (
            successor_id IS NULL
            OR successor_id <> id
        )
);

ALTER TABLE refresh_token_records
    ADD CONSTRAINT fk_refresh_token_records_successor_family
    FOREIGN KEY (
        successor_id,
        family_id
    )
    REFERENCES refresh_token_records (
        id,
        family_id
    )
    DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION enforce_refresh_token_record_family_expiration()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    family_expiration TIMESTAMPTZ;
BEGIN
    SELECT family.expires_at
    INTO family_expiration
    FROM refresh_token_families family
    WHERE family.id = NEW.family_id;

    IF NOT FOUND THEN
        RETURN NEW;
    END IF;

    IF NEW.expires_at > family_expiration THEN
        RAISE EXCEPTION
            'refresh token expiration exceeds family expiration'
            USING
                ERRCODE = '23514',
                CONSTRAINT =
                    'chk_refresh_token_records_family_expiration';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_refresh_token_records_family_expiration
    BEFORE INSERT OR UPDATE OF family_id, expires_at
    ON refresh_token_records
    FOR EACH ROW
    EXECUTE FUNCTION
        enforce_refresh_token_record_family_expiration();

CREATE FUNCTION enforce_refresh_token_family_expiration_update()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM refresh_token_records record
        WHERE record.family_id = NEW.id
          AND record.expires_at > NEW.expires_at
    ) THEN
        RAISE EXCEPTION
            'family expiration precedes token expiration'
            USING
                ERRCODE = '23514',
                CONSTRAINT =
                    'chk_refresh_token_records_family_expiration';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_refresh_token_families_expiration_update
    BEFORE UPDATE OF expires_at
    ON refresh_token_families
    FOR EACH ROW
    EXECUTE FUNCTION
        enforce_refresh_token_family_expiration_update();

CREATE INDEX idx_refresh_token_families_user_active
    ON refresh_token_families (
        user_id,
        expires_at
    )
    WHERE revoked_at IS NULL;

CREATE INDEX idx_refresh_token_families_expires_at
    ON refresh_token_families (
        expires_at
    );

CREATE INDEX idx_refresh_token_records_family_issued_at
    ON refresh_token_records (
        family_id,
        issued_at DESC
    );

CREATE INDEX idx_refresh_token_records_expires_at
    ON refresh_token_records (
        expires_at
    );
