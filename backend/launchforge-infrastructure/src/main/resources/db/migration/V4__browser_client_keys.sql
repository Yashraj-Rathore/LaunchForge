CREATE TABLE browser_client_keys (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    environment_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    client_key VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    allowed_origins JSONB NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_issuer VARCHAR(2048) NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by_issuer VARCHAR(2048),
    revoked_by_subject VARCHAR(255),
    CONSTRAINT browser_client_keys_environment_fk
        FOREIGN KEY (organization_id, project_id, environment_id)
        REFERENCES environments(organization_id, project_id, id) ON DELETE RESTRICT,
    CONSTRAINT browser_client_keys_public_unique UNIQUE (client_key),
    CONSTRAINT browser_client_keys_name_nonblank CHECK (name = BTRIM(name) AND name <> ''),
    CONSTRAINT browser_client_keys_format
        CHECK (client_key ~ '^lf_client_[A-Za-z0-9_-]{32}$'),
    CONSTRAINT browser_client_keys_fingerprint_nonblank CHECK (fingerprint <> ''),
    CONSTRAINT browser_client_keys_origins_array CHECK (
        JSONB_TYPEOF(allowed_origins) = 'array'
        AND JSONB_ARRAY_LENGTH(allowed_origins) BETWEEN 1 AND 20
    ),
    CONSTRAINT browser_client_keys_status_valid
        CHECK (status IN ('ACTIVE', 'DISABLED', 'REVOKED')),
    CONSTRAINT browser_client_keys_expiry_order
        CHECK (expires_at IS NULL OR expires_at >= created_at),
    CONSTRAINT browser_client_keys_revocation_consistent CHECK (
        (status = 'REVOKED' AND revoked_at IS NOT NULL
            AND revoked_by_issuer IS NOT NULL AND revoked_by_subject IS NOT NULL)
        OR
        (status <> 'REVOKED' AND revoked_at IS NULL
            AND revoked_by_issuer IS NULL AND revoked_by_subject IS NULL)
    )
);

CREATE INDEX browser_client_keys_environment_created_idx
    ON browser_client_keys (organization_id, environment_id, created_at DESC);

CREATE INDEX browser_client_keys_revalidation_idx
    ON browser_client_keys (id, status, expires_at);
