\set ON_ERROR_STOP on

INSERT INTO organizations (id, slug, name, status, version, created_at, updated_at)
VALUES ('61000000-0000-0000-0000-000000000001', 'prompt12-proof',
        'Prompt 12 Fictional Organization', 'ACTIVE', 0, clock_timestamp(), clock_timestamp())
ON CONFLICT (id) DO NOTHING;

INSERT INTO projects
  (id, organization_id, project_key, name, status, version, created_at, updated_at)
VALUES ('62000000-0000-0000-0000-000000000001',
        '61000000-0000-0000-0000-000000000001', 'storefront',
        'Fictional Storefront', 'ACTIVE', 0, clock_timestamp(), clock_timestamp())
ON CONFLICT (id) DO NOTHING;

INSERT INTO environments
  (id, organization_id, project_id, environment_key, name, kind, status,
   current_revision, version, created_at, updated_at)
VALUES ('63000000-0000-0000-0000-000000000001',
        '61000000-0000-0000-0000-000000000001',
        '62000000-0000-0000-0000-000000000001', 'production',
        'Fictional Production', 'PRODUCTION', 'ACTIVE', 0, 0,
        clock_timestamp(), clock_timestamp())
ON CONFLICT (id) DO NOTHING;

INSERT INTO sdk_keys
  (id, organization_id, project_id, environment_id, key_type, name, lookup_id,
   secret_verifier, pepper_version, fingerprint, status, created_at,
   created_by_issuer, created_by_subject)
VALUES ('64000000-0000-0000-0000-000000000001',
        '61000000-0000-0000-0000-000000000001',
        '62000000-0000-0000-0000-000000000001',
        '63000000-0000-0000-0000-000000000001', 'SERVER',
        'Prompt 12 local proof', 'ICEiIyQlJicoKSor',
        decode('4a11c93e444c9d5081049d3dbae764a729273a51d31b90880e0122c70b780527', 'hex'),
        'v1', 'lf_srv_ICEiIyQl...', 'ACTIVE', clock_timestamp(),
        'local-proof', 'prompt12')
ON CONFLICT (id) DO NOTHING;

INSERT INTO environment_revisions
  (organization_id, project_id, environment_id, revision, schema_version,
   snapshot_json, canonical_snapshot, checksum_sha256,
   actor_issuer, actor_subject, created_at)
VALUES (
  '61000000-0000-0000-0000-000000000001',
  '62000000-0000-0000-0000-000000000001',
  '63000000-0000-0000-0000-000000000001', 1, 1,
  '{"algorithmVersion":1,"checksum":"5138dc7312c3c55c17a8e88e3eb5380866b447982899428a0416d7edd4417bd5","environmentKey":"production","flags":{"new-checkout":{"clientVisible":false,"defaultVariation":"on","enabled":true,"offVariation":"off","rules":[],"type":"boolean","variations":[{"id":"off","value":false},{"id":"on","value":true}]}},"generatedAt":"2026-08-18T12:00:01Z","projectKey":"storefront","revision":1,"schemaVersion":1}'::jsonb,
  '{"algorithmVersion":1,"checksum":"5138dc7312c3c55c17a8e88e3eb5380866b447982899428a0416d7edd4417bd5","environmentKey":"production","flags":{"new-checkout":{"clientVisible":false,"defaultVariation":"on","enabled":true,"offVariation":"off","rules":[],"type":"boolean","variations":[{"id":"off","value":false},{"id":"on","value":true}]}},"generatedAt":"2026-08-18T12:00:01Z","projectKey":"storefront","revision":1,"schemaVersion":1}',
  '5138dc7312c3c55c17a8e88e3eb5380866b447982899428a0416d7edd4417bd5',
  'local-proof', 'prompt12', clock_timestamp())
ON CONFLICT (environment_id, revision) DO NOTHING;

UPDATE environments
   SET current_revision = 1, version = version + 1, updated_at = clock_timestamp()
 WHERE id = '63000000-0000-0000-0000-000000000001' AND current_revision < 1;

INSERT INTO outbox_events
  (id, organization_id, aggregate_type, aggregate_id, aggregate_revision,
   event_type, schema_version, payload, status, available_at, created_at)
VALUES (
  '65000000-0000-0000-0000-000000000001',
  '61000000-0000-0000-0000-000000000001', 'ENVIRONMENT',
  '63000000-0000-0000-0000-000000000001', 1,
  'config.revision-published.v1', 1,
  '{"eventId":"65000000-0000-0000-0000-000000000001","eventType":"config.revision-published.v1","schemaVersion":1,"occurredAt":"2026-08-18T12:00:01Z","organizationId":"61000000-0000-0000-0000-000000000001","projectId":"62000000-0000-0000-0000-000000000001","environmentId":"63000000-0000-0000-0000-000000000001","revision":1,"snapshotChecksum":"5138dc7312c3c55c17a8e88e3eb5380866b447982899428a0416d7edd4417bd5","traceId":"66000000-0000-0000-0000-000000000001"}'::jsonb,
  'PENDING', clock_timestamp(), clock_timestamp())
ON CONFLICT (id) DO NOTHING;
