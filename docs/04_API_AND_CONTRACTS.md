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

## Evaluation simulator

```text
POST /api/v1/environments/{environmentId}/evaluate
```

Returns flag key, variation ID, typed value, reason, matched rule, bucket if relevant, and revision. Test context is not persisted by default.

## SDK bootstrap

```text
GET /sdk/v1/snapshot
Authorization: LF-SDK <key>
If-None-Match: "revision:42:checksum"
```

Outcomes:

- 200 snapshot
- 304 unchanged
- 401 invalid/revoked key
- 403 inactive/forbidden projection
- 429 rate limited
- 503 unable to safely serve materialized snapshot

Browser client keys only receive client-visible projection.

Projection happens before checksum and ETag calculation. A server projection and browser projection for the same environment revision may therefore have different checksums/ETags, and a client must validate the exact representation it received.

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
data: {"revision":43,"etag":"...","checksum":"..."}
```

Stream carries revision hints; SDK retrieves authoritative snapshot with conditional GET.

## Analytics ingestion

```text
POST /events/v1/evaluations/batch
Authorization: LF-SDK <key>
```

Bounded count/body, no arbitrary context map, optional pseudonymous subject hash, bounded clock skew.

Analytics is off by default.

## SDK key lifecycle

```text
POST /api/v1/environments/{environmentId}/sdk-keys
GET  /api/v1/environments/{environmentId}/sdk-keys
POST /api/v1/sdk-keys/{keyId}/rotate
POST /api/v1/sdk-keys/{keyId}/revoke
```

Secret material is returned once where applicable.

## Error model

Use RFC 9457-compatible Problem Details with stable `code` and correlation ID. Never echo secrets or rejected raw targeting contexts.

## Versioning

- URL major for management
- `schemaVersion` in snapshot
- event type/schema version in Kafka
- SDK rejects unsupported future major schema and continues last-known-good
- prefer additive changes within major
