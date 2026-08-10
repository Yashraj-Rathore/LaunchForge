# ADR-0005 - Redis Is a Rebuildable Runtime Materialization

- Status: Accepted for M7
- Date: 2026-08-10

## Context

Config Edge needs fast access to current published snapshots, but configuration history and publication correctness must not depend on a cache.

## Decision

Redis stores rebuildable current snapshot/revision/checksum data and selected ephemeral distributed controls.

PostgreSQL immutable revisions remain authoritative. Kafka/projector can repopulate Redis. Edge has a controlled authoritative fallback.

Redis Pub/Sub is used only as a best-effort invalidation/revision hint.

## Consequences

- Redis loss is recoverable;
- cache improves edge latency/scaling;
- system must protect PostgreSQL from fallback storms;
- stale revision checks are mandatory;
- never store the only copy of history in Redis.
