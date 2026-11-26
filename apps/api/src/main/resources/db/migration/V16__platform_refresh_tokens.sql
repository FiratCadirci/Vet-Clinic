CREATE TABLE IF NOT EXISTS vet.platform_refresh_tokens (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_admin_id       uuid NOT NULL REFERENCES vet.platform_admins(id) ON DELETE CASCADE,
    token_hash              varchar(128) NOT NULL,
    token_family_id         uuid NOT NULL,
    expires_at              timestamptz NOT NULL,
    revoked_at              timestamptz,
    replaced_by_token_hash  varchar(128),
    created_at              timestamptz NOT NULL DEFAULT now(),
    last_used_at            timestamptz,
    CONSTRAINT uq_platform_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS ix_platform_refresh_tokens_family
    ON vet.platform_refresh_tokens (token_family_id);

CREATE INDEX IF NOT EXISTS ix_platform_refresh_tokens_admin
    ON vet.platform_refresh_tokens (platform_admin_id);
