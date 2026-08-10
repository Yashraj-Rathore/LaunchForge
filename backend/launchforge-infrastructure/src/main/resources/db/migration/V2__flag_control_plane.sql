ALTER TABLE audit_events
    ADD COLUMN project_id UUID,
    ADD COLUMN environment_id UUID,
    ADD COLUMN safe_summary VARCHAR(500),
    ADD COLUMN human_reason VARCHAR(500),
    ADD COLUMN from_revision BIGINT,
    ADD COLUMN to_revision BIGINT;

CREATE TABLE environments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    environment_key VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    current_revision BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT environments_project_fk
        FOREIGN KEY (organization_id, project_id)
        REFERENCES projects(organization_id, id) ON DELETE RESTRICT,
    CONSTRAINT environments_project_key_unique UNIQUE (project_id, environment_key),
    CONSTRAINT environments_scoped_id_unique UNIQUE (organization_id, project_id, id),
    CONSTRAINT environments_key_canonical
        CHECK (environment_key ~ '^[a-z][a-z0-9._-]{0,63}$'),
    CONSTRAINT environments_name_nonblank CHECK (name = BTRIM(name) AND name <> ''),
    CONSTRAINT environments_kind_valid
        CHECK (kind IN ('DEVELOPMENT', 'STAGING', 'PRODUCTION', 'CUSTOM')),
    CONSTRAINT environments_status_valid CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT environments_revision_nonnegative CHECK (current_revision >= 0),
    CONSTRAINT environments_version_nonnegative CHECK (version >= 0),
    CONSTRAINT environments_time_order CHECK (updated_at >= created_at)
);

CREATE TABLE flags (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    flag_key VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    flag_type VARCHAR(16) NOT NULL,
    client_visible BOOLEAN NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT flags_project_fk
        FOREIGN KEY (organization_id, project_id)
        REFERENCES projects(organization_id, id) ON DELETE RESTRICT,
    CONSTRAINT flags_project_key_unique UNIQUE (project_id, flag_key),
    CONSTRAINT flags_scoped_id_unique UNIQUE (organization_id, project_id, id),
    CONSTRAINT flags_key_canonical CHECK (flag_key ~ '^[a-z][a-z0-9._-]{0,63}$'),
    CONSTRAINT flags_name_nonblank CHECK (name = BTRIM(name) AND name <> ''),
    CONSTRAINT flags_type_valid CHECK (flag_type IN ('BOOLEAN', 'STRING', 'NUMBER', 'JSON')),
    CONSTRAINT flags_status_valid CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT flags_version_nonnegative CHECK (version >= 0),
    CONSTRAINT flags_time_order CHECK (updated_at >= created_at)
);

CREATE TABLE flag_variations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    flag_id UUID NOT NULL,
    variation_key VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    canonical_value TEXT NOT NULL,
    position INTEGER NOT NULL,
    CONSTRAINT flag_variations_flag_fk
        FOREIGN KEY (organization_id, project_id, flag_id)
        REFERENCES flags(organization_id, project_id, id) ON DELETE RESTRICT,
    CONSTRAINT flag_variations_key_unique UNIQUE (flag_id, variation_key),
    CONSTRAINT flag_variations_position_unique UNIQUE (flag_id, position),
    CONSTRAINT flag_variations_scoped_id_unique
        UNIQUE (organization_id, project_id, flag_id, id),
    CONSTRAINT flag_variations_key_canonical
        CHECK (variation_key ~ '^[a-z][a-z0-9._-]{0,63}$'),
    CONSTRAINT flag_variations_name_nonblank CHECK (name = BTRIM(name) AND name <> ''),
    CONSTRAINT flag_variations_position_nonnegative CHECK (position >= 0)
);

CREATE TABLE flag_environment_configs (
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    environment_id UUID NOT NULL,
    flag_id UUID NOT NULL,
    enabled BOOLEAN NOT NULL,
    fallthrough_variation_id UUID NOT NULL,
    off_variation_id UUID NOT NULL,
    rollout_salt VARCHAR(128) NOT NULL,
    rules JSONB NOT NULL DEFAULT '[]'::jsonb,
    rollout JSONB,
    change_summary VARCHAR(500) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (environment_id, flag_id),
    CONSTRAINT flag_environment_configs_environment_fk
        FOREIGN KEY (organization_id, project_id, environment_id)
        REFERENCES environments(organization_id, project_id, id) ON DELETE RESTRICT,
    CONSTRAINT flag_environment_configs_flag_fk
        FOREIGN KEY (organization_id, project_id, flag_id)
        REFERENCES flags(organization_id, project_id, id) ON DELETE RESTRICT,
    CONSTRAINT flag_environment_configs_fallthrough_fk
        FOREIGN KEY (organization_id, project_id, flag_id, fallthrough_variation_id)
        REFERENCES flag_variations(organization_id, project_id, flag_id, id) ON DELETE RESTRICT,
    CONSTRAINT flag_environment_configs_off_fk
        FOREIGN KEY (organization_id, project_id, flag_id, off_variation_id)
        REFERENCES flag_variations(organization_id, project_id, flag_id, id) ON DELETE RESTRICT,
    CONSTRAINT flag_environment_configs_salt_valid
        CHECK (rollout_salt ~ '^[A-Za-z0-9_-]{16,128}$'),
    CONSTRAINT flag_environment_configs_summary_nonblank
        CHECK (change_summary = BTRIM(change_summary) AND change_summary <> ''),
    CONSTRAINT flag_environment_configs_version_nonnegative CHECK (version >= 0)
);

CREATE TABLE environment_revisions (
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    environment_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    source_revision BIGINT,
    schema_version INTEGER NOT NULL,
    snapshot_json JSONB NOT NULL,
    canonical_snapshot TEXT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    human_reason VARCHAR(500),
    actor_issuer VARCHAR(2048) NOT NULL,
    actor_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (environment_id, revision),
    CONSTRAINT environment_revisions_environment_fk
        FOREIGN KEY (organization_id, project_id, environment_id)
        REFERENCES environments(organization_id, project_id, id) ON DELETE RESTRICT,
    CONSTRAINT environment_revisions_source_fk
        FOREIGN KEY (environment_id, source_revision)
        REFERENCES environment_revisions(environment_id, revision) ON DELETE RESTRICT,
    CONSTRAINT environment_revisions_revision_positive CHECK (revision > 0),
    CONSTRAINT environment_revisions_schema_version CHECK (schema_version = 1),
    CONSTRAINT environment_revisions_checksum_hex
        CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX environment_revisions_tenant_history_idx
    ON environment_revisions (organization_id, environment_id, revision DESC);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_revision BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    CONSTRAINT outbox_events_organization_fk
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT outbox_events_publication_unique
        UNIQUE (aggregate_type, aggregate_id, aggregate_revision, event_type),
    CONSTRAINT outbox_events_aggregate_valid CHECK (aggregate_type = 'ENVIRONMENT'),
    CONSTRAINT outbox_events_revision_positive CHECK (aggregate_revision > 0),
    CONSTRAINT outbox_events_schema_version CHECK (schema_version = 1),
    CONSTRAINT outbox_events_status_valid
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT outbox_events_attempt_nonnegative CHECK (attempt_count >= 0)
);

CREATE INDEX outbox_events_pending_idx
    ON outbox_events (status, available_at, created_at)
    WHERE status = 'PENDING';

CREATE FUNCTION reject_environment_revision_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'environment revisions are immutable' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER environment_revisions_immutable
BEFORE UPDATE OR DELETE ON environment_revisions
FOR EACH ROW EXECUTE FUNCTION reject_environment_revision_mutation();
