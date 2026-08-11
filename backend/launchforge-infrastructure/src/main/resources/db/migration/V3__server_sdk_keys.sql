CREATE TABLE sdk_keys (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    environment_id UUID NOT NULL,
    key_type VARCHAR(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    lookup_id VARCHAR(32) NOT NULL,
    secret_verifier BYTEA NOT NULL,
    pepper_version VARCHAR(32) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_issuer VARCHAR(2048) NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by_issuer VARCHAR(2048),
    revoked_by_subject VARCHAR(255),
    rotated_from_id UUID,
    CONSTRAINT sdk_keys_environment_fk
        FOREIGN KEY (organization_id, project_id, environment_id)
        REFERENCES environments(organization_id, project_id, id) ON DELETE RESTRICT,
    CONSTRAINT sdk_keys_rotated_from_fk
        FOREIGN KEY (rotated_from_id) REFERENCES sdk_keys(id) ON DELETE RESTRICT,
    CONSTRAINT sdk_keys_lookup_unique UNIQUE (lookup_id),
    CONSTRAINT sdk_keys_type_valid CHECK (key_type = 'SERVER'),
    CONSTRAINT sdk_keys_name_nonblank CHECK (name = BTRIM(name) AND name <> ''),
    CONSTRAINT sdk_keys_lookup_valid CHECK (lookup_id ~ '^[A-Za-z0-9_-]{16}$'),
    CONSTRAINT sdk_keys_verifier_size CHECK (OCTET_LENGTH(secret_verifier) = 32),
    CONSTRAINT sdk_keys_pepper_version_valid
        CHECK (pepper_version ~ '^[A-Za-z0-9._-]{1,32}$'),
    CONSTRAINT sdk_keys_status_valid CHECK (status IN ('ACTIVE', 'DISABLED', 'REVOKED')),
    CONSTRAINT sdk_keys_expiry_order CHECK (expires_at IS NULL OR expires_at >= created_at),
    CONSTRAINT sdk_keys_revocation_consistent CHECK (
        (status = 'REVOKED' AND revoked_at IS NOT NULL
            AND revoked_by_issuer IS NOT NULL AND revoked_by_subject IS NOT NULL)
        OR
        (status <> 'REVOKED' AND revoked_at IS NULL
            AND revoked_by_issuer IS NULL AND revoked_by_subject IS NULL)
    )
);

CREATE INDEX sdk_keys_environment_created_idx
    ON sdk_keys (organization_id, environment_id, created_at DESC);

CREATE INDEX sdk_keys_stream_revalidation_idx
    ON sdk_keys (id, status, expires_at);
