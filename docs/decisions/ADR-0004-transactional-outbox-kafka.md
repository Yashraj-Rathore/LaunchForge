# ADR-0004 - PostgreSQL Transactional Outbox Before Kafka

- Status: Accepted for M7
- Date: 2026-08-10

## Context

Publishing a revision changes authoritative PostgreSQL state and must also notify distribution consumers. A direct DB-then-Kafka dual write can lose a notification if the DB commits and Kafka fails.

## Decision

The publish transaction writes:

- immutable revision;
- current pointer;
- audit record;
- outbox row.

A separate publisher sends outbox events to Kafka and marks them published only after broker acknowledgement.

Consumers are idempotent because duplicate publication is allowed.

## Consequences

- committed revisions are not silently lost from propagation;
- broker outage creates observable lag rather than transactional rollback of management state;
- requires outbox leasing/retry/metrics;
- eventual consistency is explicit.

Kafka is introduced only after the non-Kafka runtime contract is working.
