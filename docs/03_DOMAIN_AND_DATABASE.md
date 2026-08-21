# 03 — Domain and Database

## Core entities

### Organization
`id`, name, slug, status, optimistic version, timestamps.

Statuses: Trial, Active, Suspended, Closed.

### OrganizationMembership
OIDC identity (`issuer` + `subject`) -> organization role. A membership is unique by `(organization_id, oidc_issuer, oidc_subject)`; `subject` alone is never treated as globally unique.

Roles: Owner, Admin, Developer, Viewer.

### Project
Organization-owned. Has immutable key, name, description, status, version.

### Environment
Project-owned. Has immutable key, display name, kind (Development/Staging/Production/Custom), current published revision number, version.

### Flag
Project-level identity:

- stable ID
- immutable key
- name
- type
- description
- client-visible policy
- archived state

Archived flag keys are not reused within a project.

### Variation
Stable ID, label and typed value.

### FlagEnvironmentConfig
Mutable draft/current management configuration:

- enabled
- off/default variation
- ordered rules
- rollout
- rollout salt
- optimistic version

### EnvironmentRevision
Immutable publication artifact:

- environment ID
- strictly increasing revision
- schema version
- canonical snapshot JSON
- SHA-256 checksum
- actor
- change reason
- source revision for rollback
- UTC timestamp

### SDKKey
Kinds:

- BrowserClient
- ServerSDK
- future Management credential

Store prefix/lookup metadata, the versioned HMAC verifier defined in `docs/09_SECURITY_PRIVACY.md` for secret credentials, created/revoked/last-used metadata, and environment scope.

### AuditEvent
Append-only product record.

### OutboxEvent
Transactional delivery intent: event ID/type/schema/aggregate/payload/status/lease/attempt/failure fields.

## Canonical snapshot

Contains everything an SDK needs to evaluate its authorized flags without a management API call. This section is the normative snapshot shape; other documents reference it instead of defining alternatives.

```json
{
  "schemaVersion": 1,
  "algorithmVersion": 1,
  "projectKey": "northstar-storefront",
  "environmentKey": "production",
  "revision": 42,
  "generatedAt": "2026-08-10T17:00:00Z",
  "flags": {
    "new-checkout": {
      "type": "boolean",
      "enabled": true,
      "clientVisible": true,
      "variations": [
        {"id": "off", "value": false},
        {"id": "on", "value": true}
      ],
      "offVariation": "off",
      "defaultVariation": "off",
      "rules": [],
      "rollout": {
        "attribute": "key",
        "salt": "checkout-r1",
        "weights": [
          {"variation": "on", "weight": 10000},
          {"variation": "off", "weight": 90000}
        ]
      }
    }
  },
  "checksum": "<64 lowercase hexadecimal SHA-256 characters>"
}
```

Contract rules:

- `schemaVersion` versions document shape and validation; `algorithmVersion` versions evaluator semantics.
- `flags` is an object keyed by immutable flag key. The nested flag does not repeat that key.
- flag type values are lowercase: `boolean`, `string`, `number`, or `json`.
- variation references use the `id` field and the names `offVariation` and `defaultVariation`.
- `rules` is always an array; the optional `rollout` member is omitted when no rollout is configured rather than emitted as JSON null.
- A rule has `id`, ordered `conditions`, and a `variation` machine key. A condition has `attribute`, lowercase `attributeType`, an algorithm-version-1 `operator`, and `values`; numeric operands are JSON numbers and other configured operands are strings. Conditions within a rule are ANDed and rule array order is evaluation order.
- Rollouts use `attribute`, server-owned `salt`, and ordered `weights`; each weight names a variation machine key and uses an integer bucket count.
- arrays preserve semantic declaration order. In particular, rule and rollout-weight order is significant.
- server and browser projections are separate complete snapshot representations. Filtering occurs before checksum generation, so their checksums and ETags may differ for the same environment revision.
- management-only data, internal database IDs, identities, audit data, and secret material are excluded.

### Canonical JSON and checksum

Snapshot JSON uses the JSON Canonicalization Scheme in RFC 8785. Input must satisfy the I-JSON constraints required by that scheme and the numeric restrictions in `docs/05_FLAG_EVALUATION_ENGINE.md`.

To create or verify `checksum`:

1. remove the top-level `checksum` member;
2. serialize the remaining complete projection with RFC 8785 canonical JSON;
3. encode those canonical bytes as UTF-8 with no BOM or trailing newline;
4. compute SHA-256;
5. encode the digest as exactly 64 lowercase hexadecimal characters.

All present fields, including additive fields understood by a later compatible reader, participate in the checksum. Snapshot schemas and frozen checksum fixtures must test property ordering, Unicode, numeric rendering, and both server/browser projections.

The snapshot SHA-256 checksum is a portable content-integrity contract for SDKs; it is not proof of
publication authority. Redis materializations therefore carry a separate versioned Ed25519
provenance envelope. The signature binds environment ID, revision, schema version, checksum, and
the complete canonical snapshot. A second domain-separated signature binds the lightweight
revision poll response. PostgreSQL revision rows remain the system of record.

## Identifier canonicalization

- Organization slugs match `^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$`.
- Project, environment, flag, and variation machine keys match `^[a-z][a-z0-9._-]{0,63}$`.
- Machine keys are accepted only in canonical lowercase ASCII form. APIs reject rather than silently trim or lowercase input.
- Display names remain separate human-readable fields.
- Rollout salts are server-generated URL-safe ASCII without CR/LF and are immutable unless an explicit audited cohort re-randomization is requested.
- Archived flag keys remain reserved permanently within their project.

## Suggested PostgreSQL tables

```text
organizations
organization_memberships
projects
environments
flags
flag_variations
flag_environment_configs
environment_revisions
sdk_keys
audit_events
audit_retention_previews
outbox_events
```

### M1 implemented baseline

Flyway migration `V1__tenancy_identity.sql` creates `organizations`, `organization_memberships`, `audit_events`, and the PostgreSQL-backed `launchforge_session` tables. It also creates the minimum organization-scoped `projects` table needed by later control-plane milestones. The opt-in local seed now adds one fictional project and Development environment so an authenticated operator lands in the M6 console.

The M1 JDBC adapter derives tenant access from the exact authenticated OIDC `(issuer, subject)` membership, repeats actor membership scope in tenant queries/mutations, and locks the organization row while changing the roster. Membership and tenant foreign keys use `RESTRICT`, and denied/successful membership administration writes bounded audit fields without tokens, cookies, or arbitrary claims.

### M2 implemented baseline

Flyway migration `V2__flag_control_plane.sql` adds environments, typed flags/variations, one mutable `flag_environment_configs` row per flag/environment, immutable environment revisions, and durable outbox intent. Tenant ownership is repeated on child rows and enforced through compound foreign keys as well as actor-scoped JDBC queries.

The management domain validates two to ten typed variations, algorithm-version-1 operator/type/arity rules, ordered rule/condition limits, explicit off/default references, server-owned rollout salts, and positive integer weights totalling exactly `100000`. JSON inputs use strict duplicate detection, I-JSON numeric/Unicode limits, and RFC 8785 canonicalization.

Publication locks the project/environment rows, validates the complete active draft, creates the normative keyed-flag snapshot, calculates and injects its checksum, inserts a revision, advances the environment, appends audit, and inserts a versioned pending outbox event in one PostgreSQL transaction. A database trigger rejects revision update/delete. Rollback rebases historical content with a new timestamp/checksum and strictly higher revision while preserving history and recording `source_revision`.

### M9 implemented audit-retention baseline

Flyway migration `V6__audit_retention_hardening.sql` makes audit rows immutable at the database
boundary. Updates always fail. Deletes succeed only inside a transaction that names a live,
tenant-matching retention preview whose frozen JSON candidate set includes the row and whose cutoff
predates it. `audit_retention_previews` records exact candidate IDs/count, actor, cutoff,
creation/expiry, and application time. The application locks a preview before applying it, requires
exact count confirmation, rolls back if the deleted count differs, and appends a new audit event
after governed deletion. Candidate IDs intentionally are not foreign keys so the preview remains
evidence after its selected events are removed.

### M4 implemented SDK-key baseline

Flyway migration `V3__server_sdk_keys.sql` adds one-environment server SDK-key metadata. It stores a
globally unique non-secret lookup ID, HMAC-SHA-256 verifier, non-secret pepper version and display
fingerprint, lifecycle status/expiry/revocation timestamps, optional rotation lineage, creator
identity, and a compound organization/project/environment ownership foreign key. Plaintext secret
segments are never persisted. Rotation inserts a new active key and either revokes the old key
immediately or bounds its overlap to at most 24 hours; lifecycle audit records contain metadata only.

### M5 implemented browser-key baseline

Flyway migration `V4__browser_client_keys.sql` adds a separate public browser credential class. It
stores the opaque `lf_client_...` identifier, non-secret fingerprint, exact-origin JSON array,
lifecycle timestamps, creator/revoker identity, and compound tenant/environment ownership. It has no
server secret verifier or pepper column. This separation keeps server-key hash-only invariants intact
and lets Config Edge resolve CORS policy without treating the browser identifier as confidential.
Create/revoke actions append safe audit metadata; last-used writes are coalesced.

### M6 console persistence baseline

M6 adds no new system-of-record tables. Typed variation edits update only names and canonical typed
values while preserving the exact stable variation ID/key/order set, inside the flag's optimistic
transaction. The console audit query reads existing bounded safe columns with tenant, resource,
actor, action, time, and limit filters. Draft simulation compiles an in-memory candidate snapshot
from authoritative draft rows and does not store evaluation subjects or attributes.

### M7 distribution persistence baseline

Flyway migration `V5__distribution_outbox_leases.sql` adds nullable `lease_owner` and
`lease_until` fields with a constraint requiring both fields exactly while an event is
`PROCESSING`. The claim index covers only `PENDING` and `PROCESSING` rows. A worker atomically
claims due pending or expired processing rows with `FOR UPDATE SKIP LOCKED`; only the matching lease
owner may publish, release, or permanently fail that claim. `PUBLISHED` means Kafka acknowledged
the send. `FAILED` is reserved for an invalid permanent envelope and retains only a bounded safe
error code. Kafka/Redis introduce no new system-of-record tables: immutable
`environment_revisions`, the current environment pointer, and the outbox remain authoritative.

Rule trees may initially be validated `jsonb` inside `flag_environment_configs` if domain validation remains explicit. Normalize only if query requirements justify it. Published snapshots remain immutable `jsonb`.

## Constraints

- organization slug globally unique
- organization membership unique per organization/OIDC issuer/OIDC subject
- project key unique per organization
- environment key unique per project
- flag key unique per project, including archived
- one config per flag/environment
- environment revision unique per environment/revision
- outbox event ID unique
- SDK key lookup identity unique
- browser client key globally unique with one-to-20 exact origins
- audit ID unique/immutable

Tenant-owned child rows must be protected from cross-organization parent mismatches by enforced ownership chains and compound foreign keys where a direct organization key is duplicated for scoping. Repository filters alone are not sufficient. Removing or demoting the final Owner must be rejected inside the same transaction and covered by a concurrency integration test.

## Optimistic concurrency

Mutable management resources expose opaque version/ETag. Updates require `If-Match` or explicit expected version.

On conflict, never silently overwrite. Return consistent conflict status and safe current revision metadata.

## Publish transaction

Atomically:

1. validate expected management versions
2. validate complete environment configuration
3. generate canonical snapshot
4. lock the environment row with PostgreSQL `SELECT ... FOR UPDATE`, verify its current published revision, and allocate `next_revision = current_published_revision + 1`
5. insert immutable environment revision
6. update environment published pointer
7. append audit
8. insert outbox

Failure rolls back all steps.

The environment-row lock is the version-1 publish revision allocator. Publish and rollback use the same path. Do not replace it with `MAX(revision)+1`, an in-memory counter, or a process-local lock. Concurrent integration tests must prove uniqueness and monotonicity.

## Rollback

Input references prior revision R. Application verifies schema compatibility and publishes R's content as a new revision N where `N > current`, recording `source_revision=R`.

Never move the published pointer backward without a new revision.

## Archive/deletion

- flags archive, not hard-delete in MVP
- environments archive only after relevant key controls
- projects/organizations use lifecycle state and explicit retention
- analytics retention is separate from management/audit retention
- tenant/domain foreign keys use `RESTRICT` rather than cascading hard deletion in the MVP
- closing an organization or archiving a parent does not erase revisions, audit records, or key lifecycle metadata
- any future destructive retention job requires a separate issue, preview/dry-run, tenant-safe batching, and an audit record

## IDs/time

Use UTC `Instant`. Use one consistent UUID strategy. Do not add UUIDv7 dependency unless justified.

## Migrations

Flyway, forward-only production practice. Migration job executes before new workload. No automatic destructive rollback.
