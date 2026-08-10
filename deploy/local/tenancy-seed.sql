INSERT INTO organizations (id, slug, name, status, version, created_at, updated_at)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    'northstar-commerce',
    'Northstar Commerce',
    'ACTIVE',
    0,
    TIMESTAMPTZ '2026-08-10 12:00:00Z',
    TIMESTAMPTZ '2026-08-10 12:00:00Z'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO organization_memberships
    (id, organization_id, oidc_issuer, oidc_subject, role, created_at, updated_at)
VALUES
    (
        '20000000-0000-0000-0000-000000000001',
        '10000000-0000-0000-0000-000000000001',
        'http://localhost:8081/realms/launchforge',
        '30000000-0000-0000-0000-000000000001',
        'OWNER',
        TIMESTAMPTZ '2026-08-10 12:00:00Z',
        TIMESTAMPTZ '2026-08-10 12:00:00Z'
    ),
    (
        '20000000-0000-0000-0000-000000000002',
        '10000000-0000-0000-0000-000000000001',
        'http://localhost:8081/realms/launchforge',
        '30000000-0000-0000-0000-000000000002',
        'ADMIN',
        TIMESTAMPTZ '2026-08-10 12:00:00Z',
        TIMESTAMPTZ '2026-08-10 12:00:00Z'
    ),
    (
        '20000000-0000-0000-0000-000000000003',
        '10000000-0000-0000-0000-000000000001',
        'http://localhost:8081/realms/launchforge',
        '30000000-0000-0000-0000-000000000003',
        'DEVELOPER',
        TIMESTAMPTZ '2026-08-10 12:00:00Z',
        TIMESTAMPTZ '2026-08-10 12:00:00Z'
    ),
    (
        '20000000-0000-0000-0000-000000000004',
        '10000000-0000-0000-0000-000000000001',
        'http://localhost:8081/realms/launchforge',
        '30000000-0000-0000-0000-000000000004',
        'VIEWER',
        TIMESTAMPTZ '2026-08-10 12:00:00Z',
        TIMESTAMPTZ '2026-08-10 12:00:00Z'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO projects
    (id, organization_id, project_key, name, description, status, version, created_at, updated_at)
VALUES (
    '40000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    'storefront',
    'Storefront',
    'Read-only M1 shell seed; project behavior begins in M2.',
    'ACTIVE',
    0,
    TIMESTAMPTZ '2026-08-10 12:00:00Z',
    TIMESTAMPTZ '2026-08-10 12:00:00Z'
)
ON CONFLICT (id) DO NOTHING;
