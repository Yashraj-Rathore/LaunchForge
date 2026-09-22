# Implementation Prompt 03 - Flag Control Plane

Implement **LF-0201 through LF-0207 only**.

Build the management domain for:

- projects/environments;
- typed feature flags and variations;
- environment drafts;
- ordered targeting rules;
- integer percentage rollouts;
- immutable publish revisions;
- audit + transactional outbox in the same PostgreSQL transaction;
- revision diff/history;
- rollback as a **new higher revision**.

Important boundaries:

- no Kafka publisher yet; outbox rows remain durable intent;
- no Config Edge;
- no SDK;
- no React flag editor beyond minimal API validation needs.

Required:

- domain tests;
- PostgreSQL Testcontainers;
- concurrency tests for monotonic revisions/stale writes;
- cross-tenant denial;
- immutable revision tests.

Run full relevant validation, update docs/status/changelog, and stop.
