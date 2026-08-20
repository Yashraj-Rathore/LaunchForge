\set ON_ERROR_STOP on

INSERT INTO environment_revisions
  (organization_id, project_id, environment_id, revision, source_revision, schema_version,
   snapshot_json, canonical_snapshot, checksum_sha256,
   actor_issuer, actor_subject, created_at)
VALUES (
  '61000000-0000-0000-0000-000000000001',
  '62000000-0000-0000-0000-000000000001',
  '63000000-0000-0000-0000-000000000001', 2, 1, 1,
  '{"algorithmVersion":1,"checksum":"05bbfdd0638835ca1e5d6d61d1c501afa4d007e75540cbc2663849decfeef78a","environmentKey":"production","flags":{"new-checkout":{"clientVisible":false,"defaultVariation":"on","enabled":false,"offVariation":"off","rules":[],"type":"boolean","variations":[{"id":"off","value":false},{"id":"on","value":true}]}},"generatedAt":"2026-08-18T12:00:02Z","projectKey":"storefront","revision":2,"schemaVersion":1}'::jsonb,
  '{"algorithmVersion":1,"checksum":"05bbfdd0638835ca1e5d6d61d1c501afa4d007e75540cbc2663849decfeef78a","environmentKey":"production","flags":{"new-checkout":{"clientVisible":false,"defaultVariation":"on","enabled":false,"offVariation":"off","rules":[],"type":"boolean","variations":[{"id":"off","value":false},{"id":"on","value":true}]}},"generatedAt":"2026-08-18T12:00:02Z","projectKey":"storefront","revision":2,"schemaVersion":1}',
  '05bbfdd0638835ca1e5d6d61d1c501afa4d007e75540cbc2663849decfeef78a',
  'local-proof', 'prompt12', clock_timestamp())
ON CONFLICT (environment_id, revision) DO NOTHING;

UPDATE environments
   SET current_revision = 2, version = version + 1, updated_at = clock_timestamp()
 WHERE id = '63000000-0000-0000-0000-000000000001' AND current_revision < 2;

INSERT INTO outbox_events
  (id, organization_id, aggregate_type, aggregate_id, aggregate_revision,
   event_type, schema_version, payload, status, available_at, created_at)
VALUES (
  '65000000-0000-0000-0000-000000000002',
  '61000000-0000-0000-0000-000000000001', 'ENVIRONMENT',
  '63000000-0000-0000-0000-000000000001', 2,
  'config.revision-published.v1', 1,
  '{"eventId":"65000000-0000-0000-0000-000000000002","eventType":"config.revision-published.v1","schemaVersion":1,"occurredAt":"2026-08-18T12:00:02Z","organizationId":"61000000-0000-0000-0000-000000000001","projectId":"62000000-0000-0000-0000-000000000001","environmentId":"63000000-0000-0000-0000-000000000001","revision":2,"snapshotChecksum":"05bbfdd0638835ca1e5d6d61d1c501afa4d007e75540cbc2663849decfeef78a","traceId":"66000000-0000-0000-0000-000000000002"}'::jsonb,
  'PENDING', clock_timestamp(), clock_timestamp())
ON CONFLICT (id) DO NOTHING;
