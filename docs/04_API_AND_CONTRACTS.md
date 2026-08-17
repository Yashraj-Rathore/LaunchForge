# 04 — API and Contracts

## API families

1. **Management/BFF** — humans and later management tokens.
2. **SDK data plane** — environment-scoped read-only bootstrap/stream.
3. **Analytics ingestion** — optional batched evaluation events.

Credential classes are never interchangeable.

## Common conventions

- HTTPS outside local dev
- JSON UTF-8
- `/api/v1` management
- `/sdk/v1` data plane
- `/events/v1` analytics
- UTC ISO-8601
- Problem Details-style errors with stable `code`
- correlation ID
- ETag/If-Match on mutable management resources
- ETag/If-None-Match on snapshots

The normative runtime snapshot shape, projection checksum, and identifier rules are in `docs/03_DOMAIN_AND_DATABASE.md`. The normative evaluator/reason-code contract is in `docs/05_FLAG_EVALUATION_ENGINE.md`; API examples must not define alternate names or semantics.

## Version 1 safety limits

These are validation limits for the first contract, measured on decoded/uncompressed content unless stated otherwise. Deployments may configure lower values, not silently higher ones:

| Item | Maximum |
|---|---:|
| Management JSON request body | 1 MiB |
| Canonical snapshot projection | 5 MiB UTF-8 |
| Flags in one environment snapshot | 2,000 |
| Variations per flag | 50 |
| Rules per flag | 100 |
| Conditions per rule | 10 |
| Rollout weight entries per flag | 50 |
| Evaluation-context attributes | 64 |
| Encoded evaluation context | 16 KiB UTF-8 |
| Context attribute name | 128 Unicode scalar values |
| String context value | 1,024 Unicode scalar values |
| One JSON variation value | 64 KiB canonical UTF-8 |

Exceeding a management/context limit returns a stable validation Problem Details code. A snapshot that exceeds runtime limits is rejected before publish; an SDK that receives one rejects it and retains last-known-good. Raising a version-1 hard limit requires a documented issue and Java/JavaScript boundary tests.

## Browser/session

M1 routes:

```text
GET  /api/v1/auth/me
GET  /api/v1/auth/csrf
GET  /oauth2/authorization/keycloak
POST /api/v1/auth/logout
```

The OAuth route uses Authorization Code with a public client and PKCE S256. `GET /api/v1/auth/me` returns only server-derived organization memberships for the exact authenticated issuer/subject. `GET /api/v1/auth/csrf` returns the session-bound mutation token, and logout invalidates the local PostgreSQL-backed session. Unauthenticated `/api/**` calls return `401` instead of an HTML login redirect.

## Organizations/memberships

```text
GET    /api/v1/organizations
GET    /api/v1/organizations/{orgId}
GET    /api/v1/organizations/{orgId}/members
POST   /api/v1/organizations/{orgId}/members
PATCH  /api/v1/organizations/{orgId}/members/{membershipId}
DELETE /api/v1/organizations/{orgId}/members/{membershipId}
```

These are the M1 implemented routes. Organization creation/renaming endpoints and project behavior are not exposed by Prompt 2. Membership mutations accept the target identity/role only; an organization ID supplied in the JSON body is rejected, and the path organization is authorized from the server-derived operator membership. Owner/Admin policy is enforced in the application service, UI visibility is not an authorization control, and the final Owner can never be removed or demoted.

## Projects/environments

```text
POST  /api/v1/organizations/{orgId}/projects
GET   /api/v1/organizations/{orgId}/projects
PATCH /api/v1/projects/{projectId}
POST  /api/v1/projects/{projectId}/environments
GET   /api/v1/projects/{projectId}/environments
PATCH /api/v1/environments/{environmentId}
```

## Flags

```text
POST  /api/v1/projects/{projectId}/flags
GET   /api/v1/projects/{projectId}/flags
GET   /api/v1/flags/{flagId}
PATCH /api/v1/flags/{flagId}
POST  /api/v1/flags/{flagId}/archive
GET   /api/v1/flags/{flagId}/environments/{environmentId}
PUT   /api/v1/flags/{flagId}/environments/{environmentId}
POST  /api/v1/flags/{flagId}/environments/{environmentId}/rollout/reseed
```

## Publish/revision

```text
POST /api/v1/environments/{environmentId}/publish
GET  /api/v1/environments/{environmentId}/revisions
GET  /api/v1/environments/{environmentId}/revisions/{revision}
GET  /api/v1/environments/{environmentId}/revisions/diff?from={revision}&to={revision}
POST /api/v1/environments/{environmentId}/rollback
```

Production change reason is required.

These project/environment/flag/draft/publication routes are implemented by M2. Mutable updates, draft replacement, publication, reseeding, and rollback require `If-Match`; missing preconditions return `428` and stale versions return `409`. Request bodies never accept tenant ownership or rollout salt. Cohort reseeding is a separate reason-required audited operation. History summaries omit snapshot content, a single-revision read returns the immutable canonical snapshot, and structured diff reports added, removed, and changed flag keys without exposing actor or audit internals.

Flag creation accepts `BOOLEAN`, `STRING`, `NUMBER`, or `JSON`, a `clientVisible` decision, and two to ten variations. JSON token types must exactly match the declared flag type; there is no implicit coercion.

M6 extends flag update with an optional `variations` array containing each existing stable variation
ID plus its new name and typed value. The submitted ID set must exactly match the flag's existing
variation set; keys, order, identity, and flag type remain immutable. The flag row and variation
rows update in the same optimistic transaction.

## Evaluation simulator

```text
POST /api/v1/environments/{environmentId}/evaluate
```

Returns flag key, variation ID, typed value, reason, matched rule, bucket if relevant, and revision. Test context is not persisted by default.

M6 implements this route as an authenticated, CSRF-protected management mutation. It validates and
compiles the complete current draft as the next candidate revision, then invokes the same pure Java
snapshot parser/evaluator used by the Java SDK. The request carries `flagKey`, declared `type`, a
typed caller `defaultValue`, and `context` with a non-blank subject key plus bounded scalar
attributes. The response explicitly identifies `configuration: "DRAFT"`, the current published
revision, and the candidate revision. Evaluation context is neither persisted nor logged.

## Audit query

```text
GET /api/v1/organizations/{organizationId}/audit
    ?projectId={projectId}
    &environmentId={environmentId}
    &actor={exactSubject}
    &action={exactAction}
    &from={instant}
    &to={instant}
    &limit={1..200}
```

M6 implements this tenant-authorized read for the console. Optional resource, actor, action, and
time filters are combined, newest events are returned first, and output is limited to safe audit
metadata. Cross-organization direct IDs remain not-found and response fields never contain request
bodies, credentials, cookies, authorization headers, or simulator context.

M9 validates every optional project/environment filter against the authenticated organization
before querying. It also provides a bounded CSV representation of the same safe projection:

```text
GET /api/v1/organizations/{organizationId}/audit/export
    ?projectId={projectId}&environmentId={environmentId}&actor={exactSubject}
    &action={exactAction}&from={instant}&to={instant}&limit={1..200}
```

The response is `text/csv;charset=UTF-8`, is attachment-dispositioned, quotes every field, and
prefixes spreadsheet formula-leading cells. Export never bypasses normal tenant authorization or
filter validation.

Governed retention uses two CSRF-protected management mutations:

```text
POST /api/v1/organizations/{organizationId}/audit/retention/preview
{"deleteBefore":"2025-01-01T00:00:00Z","limit":1000}

POST /api/v1/organizations/{organizationId}/audit/retention/{previewId}/apply
{"expectedCandidateCount":37}
```

Only Owner/Admin roles may use these routes. The default minimum age is 365 days, preview lifetime
is 15 minutes, and one preview contains at most 1,000 exact event IDs. Applying is disabled unless
`LAUNCHFORGE_AUDIT_RETENTION_DELETION_ENABLED=true`. Missing/cross-tenant previews return `404`;
disabled, expired, already-applied, count-mismatched, or changed candidate sets return `409` with
`AUDIT_RETENTION_CONFLICT`.

## SDK bootstrap

```text
GET /sdk/v1/snapshot
Authorization: LF-SDK <key>
If-None-Match: "env_<opaque>_rev_42_<checksum>"
```

Outcomes:

- 200 snapshot
- 304 unchanged
- 401 invalid/revoked key
- 403 inactive/forbidden projection
- 429 rate limited
- 503 unable to safely serve materialized snapshot

A `200` or `304` includes `ETag`, `X-LaunchForge-Revision`,
`X-LaunchForge-Checksum`, `X-LaunchForge-Schema-Version`, and
`Cache-Control: no-store`. The response body is capped at 1 MiB in M4 and is the
validated canonical server projection stored in the immutable PostgreSQL revision.

Browser client keys only receive client-visible projection.

M9 applies independent Redis-backed fixed-window policies to snapshots, stream starts, and
analytics ingestion after credential resolution, so the partition is the trusted server/browser
key ID. A rejection returns `429`, `Retry-After`, and the stable endpoint-class code
`SNAPSHOT_RATE_LIMITED`, `STREAM_RATE_LIMITED`, or `ANALYTICS_RATE_LIMITED`. Redis failure falls
back to bounded per-process counters; it never skips all limiting.

Projection happens before checksum and ETag calculation. A server projection and browser projection for the same environment revision may therefore have different checksums/ETags, and a client must validate the exact representation it received.

M5 implements the distinct public browser endpoints:

```text
GET /sdk/v1/client/{clientKey}/snapshot
GET /sdk/v1/client/{clientKey}/stream
```

`clientKey` has the public `lf_client_<32 base64url characters>` form and is mapped to exactly one
environment. It is intentionally carried in the path so Config Edge can resolve that key's origin
policy for CORS preflight; it is not a secret authenticator. Both endpoints accept only `GET`, never
cookies or credentialed CORS. Snapshot responses use the same headers and conditional request
semantics as the server route. The browser stream has the same revision-only event shape and accepts
`Last-Event-ID`; the browser SDK implements it with streaming `fetch` and `credentials: omit`.

## Revision stream

```text
GET /sdk/v1/stream
Authorization: LF-SDK <key>
Accept: text/event-stream
Last-Event-ID: 42
```

Example:

```text
event: revision
id: 43
data: {"revision":43}
```

The stream carries revision hints only. It also emits heartbeat comments; the SDK retrieves the
authoritative snapshot with a conditional GET. `Last-Event-ID` is a convergence hint, never an
ordering authority.

## Internal revision event

M7 publishes `config.revision-published.v1` records to
`launchforge.config.revision-published.v1`, keyed by the canonical environment UUID so all events
for one environment share a Kafka partition. Required fields are event ID/type/schema version, UTC
occurrence time, organization/project/environment IDs, positive revision, snapshot checksum, and a
bounded trace ID. The event contains no snapshot body, SDK credential, OIDC material, evaluation
context, or full audit payload. The projector reloads and validates the immutable PostgreSQL
revision before advancing Redis.

The authoritative version-1 artifacts are:

- `contracts/events/config-revision-published-v1.schema.json`;
- `contracts/events/config-revision-published-v1.example.json`;
- `dev.launchforge.contracts.events.ConfigRevisionPublishedEvent`.

Unknown additive fields are accepted within version 1. Missing/invalid required fields and an
unsupported event type or schema version are permanent contract failures; an incompatible change
requires a new versioned event type/topic.

## Analytics ingestion

```text
POST /events/v1/evaluations/batch
Authorization: LF-SDK <key>
Content-Type: application/json

POST /events/v1/client/{clientKey}/evaluations/batch
Origin: https://allowed.example
Content-Type: application/json
```

Both routes accept the version-1 batch in
`contracts/events/evaluation-event-batch-v1.schema.json`. A batch contains 1–100 events and the
body is capped at 256 KiB. Each event contains only a UUID event ID, timestamp, bounded flag key,
optional bounded variation ID, version-1 reason code, and positive revision. Subject identifiers,
pseudonymous hashes, evaluation-context attributes, organization IDs, project IDs, and environment
IDs are not client-supplied fields. Unknown fields are rejected. Config Edge derives tenant scope
from the authenticated environment-scoped server key or the public browser key and its exact-origin
policy.

Accepted batches return `202` with `batchId` and `acceptedEvents`. Invalid batches return
`ANALYTICS_BATCH_INVALID`; exhausted per-key/global capacity returns
`ANALYTICS_CAPACITY_EXHAUSTED`; a bounded Kafka publication failure returns
`ANALYTICS_UNAVAILABLE`. Analytics responses and failures never change an evaluation result.

The operator query is separate and tenant-authorized:

```text
GET /api/v1/environments/{environmentId}/analytics/evaluations
    ?from={instant}
    &to={instant}
    &flagKey={optional}
    &variationId={optional}
    &bucket={HOUR|DAY}
    &limit={1..1000}
```

The default range is the prior 24 hours and the maximum range is 31 days. Results are unique-event
counts grouped by bucket, flag, and variation; duplicate delivery is tolerated by aggregating
`uniqExact(event_id)`. The query path has its own concurrency, request timeout, ClickHouse execution,
and result-row bounds. Its response explicitly labels the counts as operational and makes no
experiment-significance or causal claim. Disabled/unavailable analytics returns
`ANALYTICS_UNAVAILABLE`; capacity shedding returns `ANALYTICS_QUERY_CAPACITY_EXHAUSTED`.

Analytics is off by default.

## SDK key lifecycle

```text
POST /api/v1/environments/{environmentId}/sdk-keys
GET  /api/v1/environments/{environmentId}/sdk-keys
POST /api/v1/sdk-keys/{keyId}/rotate
POST /api/v1/sdk-keys/{keyId}/revoke

POST /api/v1/environments/{environmentId}/client-keys
GET  /api/v1/environments/{environmentId}/client-keys
POST /api/v1/client-keys/{keyId}/revoke
```

Secret material is returned once where applicable.

Create accepts `{"name":"Storefront server","expiresAt":null}` and returns `201` with a metadata
object plus the one-time `secret`. List returns metadata only and never the verifier or secret.
Rotate accepts optional `overlapSeconds` (zero through 86400) and optional replacement
`expiresAt`; it returns the new credential once. Revoke is idempotent and returns `204`.

Each `lf_srv_<lookup_id>_<secret>` credential maps to exactly one
environment. Owner/Admin may manage all environment keys; Developer is constrained to
non-production environments; Viewer is denied. M5 browser-key create accepts
`{"name":"Storefront browser","allowedOrigins":["https://shop.example"],"expiresAt":null}`.
The response and subsequent list contain the public key, fingerprint, exact-origin policy, and safe
lifecycle metadata. Revoke is idempotent. A browser key is a separate credential class: server
snapshot routes reject it, browser routes reject server keys, and the management API still requires
an authenticated operator session plus CSRF for mutations.

## Error model

Use RFC 9457-compatible Problem Details with stable `code` and correlation ID. Never echo secrets or rejected raw targeting contexts.

## Versioning

- URL major for management
- `schemaVersion` in snapshot
- event type/schema version in Kafka
- SDK rejects unsupported future major schema and continues last-known-good
- prefer additive changes within major
