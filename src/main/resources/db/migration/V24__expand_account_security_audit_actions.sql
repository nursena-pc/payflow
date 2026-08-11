ALTER TABLE account_security_audits
    DROP CONSTRAINT chk_account_security_audits_action;

ALTER TABLE account_security_audits
    ADD CONSTRAINT chk_account_security_audits_action
    CHECK (action IN (
        'MFA_DISABLED',
        'RECOVERY_CODES_ROTATED'
    ));