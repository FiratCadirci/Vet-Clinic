CREATE TABLE IF NOT EXISTS vet.refresh_tokens (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         uuid NOT NULL REFERENCES vet.organizations(id),
    user_id                 uuid NOT NULL,
    token_hash              varchar(128) NOT NULL,
    token_family_id         uuid NOT NULL,
    expires_at              timestamptz NOT NULL,
    revoked_at              timestamptz,
    replaced_by_token_hash  varchar(128),
    created_at              timestamptz NOT NULL DEFAULT now(),
    last_used_at            timestamptz,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT uq_refresh_tokens_org_id UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, user_id)
        REFERENCES vet.app_users (organization_id, id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_lookup
    ON vet.refresh_tokens (organization_id, token_hash);

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_family
    ON vet.refresh_tokens (organization_id, token_family_id);

ALTER TABLE vet.refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE vet.refresh_tokens FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON vet.refresh_tokens;
CREATE POLICY tenant_isolation ON vet.refresh_tokens
    USING (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid);

DROP POLICY IF EXISTS platform_admin_select ON vet.refresh_tokens;
CREATE POLICY platform_admin_select ON vet.refresh_tokens
    FOR SELECT
    TO vet_app
    USING ((SELECT vet.is_platform_admin()));

DROP POLICY IF EXISTS platform_admin_insert ON vet.refresh_tokens;
CREATE POLICY platform_admin_insert ON vet.refresh_tokens
    FOR INSERT
    TO vet_app
    WITH CHECK ((SELECT vet.is_platform_admin()));

DROP POLICY IF EXISTS platform_admin_update ON vet.refresh_tokens;
CREATE POLICY platform_admin_update ON vet.refresh_tokens
    FOR UPDATE
    TO vet_app
    USING ((SELECT vet.is_platform_admin()))
    WITH CHECK ((SELECT vet.is_platform_admin()));

DROP POLICY IF EXISTS platform_admin_delete ON vet.refresh_tokens;
CREATE POLICY platform_admin_delete ON vet.refresh_tokens
    FOR DELETE
    TO vet_app
    USING ((SELECT vet.is_platform_admin()));

