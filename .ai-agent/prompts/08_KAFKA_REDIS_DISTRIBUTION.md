# Implementation Prompt 08 - Kafka and Redis Distribution

Implement **LF-0701 through LF-0706 only**.

Build the distributed propagation layer:

- transactional outbox publisher;
- versioned Kafka revision event;
- partition by environment;
- idempotent projector;
- Redis current snapshot/revision/checksum materialization;
- bounded Redis invalidation hint;
- Config Edge Redis fast path + controlled PostgreSQL fallback;
- at least two edge instances in integration/demo;
- broker/cache failure drills.

Preserve the already-working SDK/snapshot semantics. Do not make Redis authoritative.

Required proof:

- DB commit while Kafka is down produces pending outbox, then catches up;
- duplicate/replayed event is safe;
- Redis flush rebuilds;
- two edges converge;
- SDK retains local evaluation.

Update runbooks/status/changelog and stop.
