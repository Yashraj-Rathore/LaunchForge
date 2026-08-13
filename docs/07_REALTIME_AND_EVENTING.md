# 07 - Real-Time Distribution and Eventing

## 1. Objective

A published flag change should become visible to SDKs quickly without making every flag evaluation depend on LaunchForge availability.

This document separates:

1. management writes;
2. durable change propagation;
3. edge projection;
4. client notification;
5. authoritative snapshot fetch.

## 2. Publish transaction

A management publish is complete only when PostgreSQL commits:

- the new immutable environment revision;
- its canonical snapshot payload or rebuildable normalized representation;
- audit metadata;
- an outbox event.

All are committed atomically.

Kafka is never written inside the database transaction.

## 3. Outbox pattern

The outbox protects against this failure:

```text
DB commit succeeds
    |
Kafka publish fails
    |
without outbox -> change may be lost
```

Instead:

```text
PostgreSQL transaction
  - revision 43
  - audit event
  - outbox row
       |
       v
Outbox publisher
       |
       v
Kafka
```

The publisher retries until acknowledgement. It records publication state only after the broker acknowledges.

Consumers must remain idempotent because the contract is at-least-once.

### M7 implemented publisher

M2 writes one `PENDING` outbox row in the same PostgreSQL transaction as the immutable revision,
current-revision pointer, and audit event. M7 completes that boundary in
`launchforge-event-worker`. Workers claim bounded batches with one PostgreSQL
`UPDATE ... FOR UPDATE SKIP LOCKED` statement, attach an owner and expiry, and may reclaim only an
expired lease. A row becomes `PUBLISHED` only after the Kafka send future returns a broker
acknowledgement. Transient broker failures release the lease with bounded exponential backoff;
malformed or unsupported envelopes become visible `FAILED` rows instead of being retried forever.
Worker termination before acknowledgement or before the status update can create a duplicate, so
consumers remain idempotent by design.

## 4. Kafka role

Kafka is used for durable internal propagation after the core system is working without it.

Initial topic family:

```text
launchforge.config.revision-published.v1
launchforge.project.lifecycle.v1
launchforge.key.lifecycle.v1
```

Do not create a topic per tenant or project.

M7 creates `launchforge.config.revision-published.v1` with 12 partitions and replication factor
one for the single-broker local Compose profile. Production must configure the partition count and
a replication factor supported by the deployed broker cluster; it must not copy the local
single-replica assumption. The producer requires `acks=all` and idempotence. The projector group is
`launchforge-config-projector-v1`, disables auto-commit, uses record acknowledgement, and starts at
the earliest retained event when it has no committed offset.

### Event envelope

Every event should include:

```json
{
  "eventId": "uuid",
  "eventType": "config.revision-published.v1",
  "schemaVersion": 1,
  "occurredAt": "...",
  "organizationId": "...",
  "projectId": "...",
  "environmentId": "...",
  "revision": 43,
  "snapshotChecksum": "64 lowercase hexadecimal characters",
  "traceId": "..."
}
```

The machine-readable schema and example are
`contracts/events/config-revision-published-v1.schema.json` and
`contracts/events/config-revision-published-v1.example.json`. Version 1 consumers tolerate unknown
additive fields, but reject a different event type/schema version or invalid required fields.

Do not place SDK keys, OIDC tokens, arbitrary user attributes, or full audit payloads in Kafka.

## 5. Partitioning and ordering

For configuration revision events, partition by stable environment identifier.

This provides ordered delivery for revisions belonging to one environment while allowing different environments to progress independently.

Consumers still compare revision numbers because:

- retries happen;
- replays happen;
- duplicated events happen;
- a stale consumer may catch up after restart.

## 6. Redis role

Redis is a **rebuildable acceleration layer**.

It may contain:

- current compiled snapshot by environment;
- current revision metadata;
- ETag/checksum metadata;
- bounded rate-limit state;
- Pub/Sub invalidation hints.

Redis is not:

- the authoritative revision history;
- the only copy of published configuration;
- a replacement for the outbox;
- a secrets vault.

A total Redis flush must be recoverable from PostgreSQL/Kafka.

M7 stores one hash per environment at
`launchforge:config:snapshot:<environment-uuid>` with `revision`, `schemaVersion`, `checksum`, and
the canonical `snapshot`. A Lua compare-and-set writes and notifies only when the incoming revision
is strictly newer, preventing stale consumers and PostgreSQL fallbacks from regressing state.

## 7. Projection consumer

The configuration projection consumer:

1. receives a revision event;
2. compares it with the locally/Redis-known revision;
3. loads authoritative revision content if necessary;
4. validates it;
5. materializes the current snapshot in Redis;
6. emits a best-effort Redis invalidation notification;
7. records metrics;
8. commits Kafka progress only after safe processing.

A duplicate or older event is a no-op.

The projector validates the Kafka key, event envelope, PostgreSQL organization/project/revision and
snapshot checksum before materialization. A bounded scheduled reconciliation scan loads current
immutable revisions directly from PostgreSQL, so Redis can be rebuilt even when retained Kafka
history is insufficient.

## 8. Config Edge service

`launchforge-config-edge` is a separate Spring Boot/WebFlux deployable because its workload differs from the management API:

- high read volume;
- many long-lived SSE connections;
- no management writes;
- SDK-key authentication;
- strict runtime response contracts.

It serves:

```text
GET /sdk/v1/snapshot
GET /sdk/v1/stream
POST /sdk/v1/events   # optional analytics, later
```

The edge must be horizontally scalable and stateless except for ephemeral connection state.

### M4 contract and M7 scale-out implementation

LF-0401 through LF-0406 implement this as an independent Spring Boot WebFlux process. The M4 edge
authenticates the structured server key, validates the immutable canonical snapshot and checksum,
and reads the current published revision directly from PostgreSQL on a bounded elastic scheduler.
Each bounded SSE connection periodically revalidates key/scope lifecycle and checks the current
environment revision; only strictly newer revision notices are emitted. M7 preserves that public
contract while adding a Redis-first snapshot/revision path and a single global Pub/Sub hint
subscription. Redis misses, malformed values, and connection failures use a semaphore-bounded
PostgreSQL fallback with a bounded acquire timeout. A successful fallback backfills Redis only when
its revision is newer. Periodic revision checks remain active, so a lost Pub/Sub hint cannot prevent
convergence.

## 9. Snapshot resolution

Fast path:

```text
SDK request
   -> authenticate key
   -> resolve environment
   -> Redis current snapshot
   -> response
```

Fallback path:

```text
Redis miss/unavailable
   -> PostgreSQL published revision
   -> validate/materialize
   -> response
```

Redis failure should increase latency before it causes unavailability.

## 10. ETag contract

Snapshot responses should include:

```text
ETag: "env_<opaque>_rev_43_<checksum>"
X-LaunchForge-Revision: 43
Cache-Control: no-store
```

SDKs may send `If-None-Match`.

A matching current snapshot returns `304`.

Do not rely on browser/shared-proxy caching for server SDK configuration.

## 11. SSE stream semantics

SSE is intentionally thin.

Events contain revision metadata, not the full configuration.

Example:

```text
id: 43
event: revision
data: {"revision":43}
```

Heartbeat comments keep compatible intermediaries from declaring idle connections dead.

### Stream rules

- authenticate before opening;
- bind one connection to one environment/key scope;
- cap connections per key/IP as appropriate;
- disconnect revoked keys quickly;
- send no raw secrets or context;
- tolerate duplicate events;
- support `Last-Event-ID` only as a hint;
- never assume SSE itself provides durable delivery.

If a client misses events, its next snapshot fetch converges it to current state.

## 12. Why not WebSockets

Feature flag distribution is primarily server-to-client notification. SSE provides:

- simpler HTTP semantics;
- easier reverse proxy support;
- automatic browser reconnection semantics;
- no need for client-to-server message frames.

WebSockets can be revisited if future product requirements require bidirectional low-latency control.

## 13. Redis Pub/Sub

Each edge instance subscribes to the one global namespaced channel
`launchforge:config:revision-hints:v1`. Its bounded version-1 JSON payload contains only schema
version, environment ID, and revision; messages larger than 512 bytes or with invalid fields are
discarded.

Do not create an unbounded subscription channel per environment.

Pub/Sub is best-effort. Losing a Pub/Sub notification must not lose configuration because:

- Kafka/projector owns durable propagation;
- Redis/PostgreSQL own current state;
- SDK polling provides eventual convergence.

## 14. Failure scenarios

### Kafka unavailable during publish

Management DB publish succeeds because outbox row commits. Outbox remains pending and retries. Dashboard must distinguish "published in control plane" from delayed distribution if delay exceeds alert threshold.

### Projector down

Kafka retains events. Current snapshots remain at prior revision. When projector resumes it processes revisions in order and catches up.

### Redis unavailable

Edge falls back to authoritative database access with rate/circuit protection. SDKs keep LKG. Alert on sustained fallback.

### One edge instance misses Redis Pub/Sub

SDKs connected to it still poll and eventually fetch current revision. Edge may also periodically verify revision watermarks.

### SSE network interruption

SDK reconnects with jitter and immediately checks the current snapshot.

### Duplicate Kafka event

Revision check makes projection idempotent.

### Revision 44 arrives after 45 due to replay

Projection ignores 44 because current revision is already 45.

## 15. Backpressure

Protect the platform from connection and event storms:

- bounded thread/event-loop resources;
- connection quotas;
- broker consumer lag alerts;
- bounded retries;
- no unbounded in-memory event buffers;
- load shedding for optional analytics before config distribution;
- staggered polling;
- jittered reconnects.

## 16. Delivery SLO candidates

These become contractual only after measurement and operational maturity.

Track:

- publish-to-projection latency;
- projection-to-edge visibility;
- publish-to-SDK convergence;
- connected stream count;
- stream reconnect rate;
- snapshot p50/p95/p99 latency;
- Kafka consumer lag;
- Redis fallback rate.

A demo may target visible convergence within seconds, but do not place an unmeasured number on a resume.

## 17. Schema evolution

Kafka event types are versioned in their names/envelopes.

Rules:

- consumers ignore unknown additive fields;
- incompatible changes create a new event version;
- producer/consumer contract tests run in CI;
- replay of retained old events remains safe.

## 18. Local development

Kafka and Redis are opt-in through the `distribution` Compose profile. PostgreSQL remains the
system of record and also starts because it has no profile:

```powershell
docker compose --profile distribution up -d --wait
```

With the database, Kafka, Redis, shared SDK-key pepper, and database variables from `.env.example`
exported, package and start the worker and Config Edge in separate terminals:

```powershell
.\mvnw.cmd -pl backend/launchforge-event-worker,backend/launchforge-config-edge -am package
java -jar backend/launchforge-event-worker/target/launchforge-event-worker-0.1.0-SNAPSHOT-exec.jar
java -jar backend/launchforge-config-edge/target/launchforge-config-edge-0.1.0-SNAPSHOT-exec.jar
```

The automated durability drill is:

```powershell
.\mvnw.cmd -pl tests/integration-tests -am verify -Pintegration "-Dit.test=DistributionPipelineIT" "-Dfailsafe.failIfNoSpecifiedTests=false"
```

Stop the local services without deleting PostgreSQL data with
`docker compose --profile distribution down`.
