CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    slug VARCHAR(63) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT organizations_slug_canonical
        CHECK (slug ~ '^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$'),
    CONSTRAINT organizations_name_nonblank CHECK (name = BTRIM(name) AND name <> ''),
    CONSTRAINT organizations_status_valid CHECK (status IN ('TRIAL', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT organizations_version_nonnegative CHECK (version >= 0),
    CONSTRAINT organizations_time_order CHECK (updated_at >= created_at)
);

CREATE TABLE organization_memberships (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    oidc_issuer VARCHAR(2048) NOT NULL,
    oidc_subject VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT organization_memberships_organization_fk
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT organization_memberships_identity_unique
        UNIQUE (organization_id, oidc_issuer, oidc_subject),
    CONSTRAINT organization_memberships_scoped_id_unique UNIQUE (organization_id, id),
    CONSTRAINT organization_memberships_role_valid
        CHECK (role IN ('OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER')),
    CONSTRAINT organization_memberships_issuer_nonblank
        CHECK (oidc_issuer = BTRIM(oidc_issuer) AND oidc_issuer <> ''),
    CONSTRAINT organization_memberships_subject_nonblank
        CHECK (oidc_subject = BTRIM(oidc_subject) AND oidc_subject <> ''),
    CONSTRAINT organization_memberships_time_order CHECK (updated_at >= created_at)
);

CREATE INDEX organization_memberships_identity_idx
    ON organization_memberships (oidc_issuer, oidc_subject, organization_id);

-- M1 stores one optional read-only local project seed. Project behavior remains owned by M2.
CREATE TABLE projects (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_key VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT projects_organization_fk
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT projects_organization_key_unique UNIQUE (organization_id, project_key),
    CONSTRAINT projects_scoped_id_unique UNIQUE (organization_id, id),
    CONSTRAINT projects_key_canonical
        CHECK (project_key ~ '^[a-z][a-z0-9._-]{0,63}$'),
    CONSTRAINT projects_status_valid CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT projects_version_nonnegative CHECK (version >= 0),
    CONSTRAINT projects_time_order CHECK (updated_at >= created_at)
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    actor_issuer VARCHAR(2048) NOT NULL,
    actor_subject VARCHAR(255) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id UUID NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT audit_events_organization_fk
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT
);

CREATE INDEX audit_events_organization_time_idx
    ON audit_events (organization_id, created_at DESC);

CREATE TABLE launchforge_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INTEGER NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(255),
    CONSTRAINT launchforge_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX launchforge_session_session_id_idx
    ON launchforge_session (session_id);
CREATE INDEX launchforge_session_expiry_idx
    ON launchforge_session (expiry_time);
CREATE INDEX launchforge_session_principal_idx
    ON launchforge_session (principal_name);

CREATE TABLE launchforge_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT launchforge_session_attributes_pk
        PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT launchforge_session_attributes_fk
        FOREIGN KEY (session_primary_id)
        REFERENCES launchforge_session(primary_id) ON DELETE CASCADE
);
