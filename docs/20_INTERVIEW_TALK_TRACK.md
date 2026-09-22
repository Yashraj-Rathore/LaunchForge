# 20 - Interview Talk Track

## 1. 30-second explanation

> LaunchForge is a feature flag and remote-configuration platform I built with Java/Spring Boot and React. The control plane stores immutable published revisions, while application SDKs evaluate flags locally so a customer request does not depend on LaunchForge. Configuration changes propagate through a transactional outbox, Kafka and Redis to WebFlux edge nodes, which notify SDKs over SSE to fetch a new authoritative snapshot.

Only use the distributed-systems sentence after those milestones exist.

## 2. Why this project

Good answer:

> I wanted a modern Java/Spring project that was not another CRUD or payment workflow. Feature flags force you to think about deterministic evaluation, SDK design, consistency, safe publishing, cache invalidation and failure modes. I also designed it so it could become a real developer product.

## 3. Key architecture decision: local evaluation

Question: Why not call LaunchForge every time?

Answer themes:

- latency;
- application availability;
- traffic/cost;
- predictable behavior;
- SDK LKG;
- updates distributed separately from evaluation.

Tradeoff:

- clients may be briefly stale;
- snapshot security/compatibility matters;
- propagation needs observability.

## 4. Why immutable revisions

- reproducibility;
- audit;
- safe rollback;
- debugging;
- no ambiguous in-place production mutation.

Rollback creates a new revision to preserve monotonic event ordering/history.

## 5. Why outbox

Explain dual-write problem:

```text
DB commit + Kafka publish cannot be assumed atomic.
```

The DB transaction writes revision + outbox. Publisher retries Kafka. Consumers are idempotent.

## 6. Why Kafka

Not because "Kafka is scalable."

Use:

- durable async propagation;
- replay;
- consumer groups;
- ordering by environment partition;
- decoupled projector.

Also explain that the core system was built before Kafka.

## 7. Why Redis

- current snapshot materialization;
- fast edge reads;
- distributed ephemeral state;
- rebuildable.

It is not authoritative.

## 8. Why SSE

- server-to-client notifications;
- standard HTTP;
- simpler than WebSockets for one-way change events;
- reconnect semantics.

SSE sends revision hints, not full snapshots, so missed events are harmless; clients fetch current state.

## 9. Deterministic rollout

Be able to draw:

```text
flagKey + salt + subject
        |
      SHA-256
        |
first unsigned 64 bits
        |
     % 100000
        |
      bucket
        |
cumulative variation ranges
```

Explain why stable salt matters.

## 10. Cross-language correctness

Java and JS evaluators use one language-neutral golden corpus. This prevents a server user and browser user with the same context from receiving different variants because of implementation drift.

## 11. Failure scenario: Kafka down

Expected answer:

- publish DB transaction commits;
- outbox remains;
- distribution delayed;
- current SDK config remains;
- after broker recovery outbox publishes;
- projector catches up;
- alerts expose lag.

## 12. Failure scenario: Redis down

- edge falls back in controlled fashion;
- SDK LKG limits dependency;
- Redis can rebuild;
- prevent DB thundering herd.

## 13. Failure scenario: LaunchForge entirely unreachable

After an SDK has a snapshot:

- local evaluation continues;
- LKG/current snapshot remains;
- no new config reaches app;
- caller defaults apply only if no usable snapshot/flag.

## 14. Security question

Discuss:

- OIDC BFF/session;
- server-derived tenant context;
- hashed SDK keys;
- public vs server client keys;
- audit;
- CSRF/CSP/CORS;
- no flag as authorization boundary;
- no context logging.

## 15. Consistency model

The management DB is strongly transactional for publish.

Distribution is eventually consistent.

Revision numbers/checksums provide convergence and observability.

This is an intentional availability/latency tradeoff.

## 16. Scaling question

Scale Config Edge horizontally because it is read/connection heavy.

Kafka partitions distribute environments.

Redis serves current snapshots.

PostgreSQL remains control-plane source of truth.

Do not claim arbitrary millions of clients without benchmarks.

## 17. What would you change for multi-region

Possible future:

- regional edge/cache;
- globally routed SDK endpoints;
- replicated/durable event distribution;
- authoritative publish region or clearly defined conflict policy;
- regional revision watermarks;
- careful key revocation propagation.

State clearly this is future design unless implemented.

## 18. Testing question

Mention:

- pure evaluator unit tests;
- shared golden vectors;
- Testcontainers PostgreSQL/Kafka/Redis;
- Playwright;
- failure injection;
- ArchUnit;
- JMH;
- SSE/load tests.

## 19. Tradeoff question

Three strong examples:

1. full snapshot fetch after revision notification rather than complex deltas;
2. modular control plane before microservices;
3. Kafka/Redis added only after baseline runtime semantics.

## 20. What was hardest

Use the real answer after building. Likely candidates:

- defining exact cross-language evaluation semantics;
- safe immutable publication/concurrency;
- stream reconnect + atomic snapshot activation;
- preserving behavior under infrastructure outage.

Never pretend a planned challenge was actually experienced.

## 21. How to discuss AI-assisted implementation

Be transparent:

> I used coding tools to accelerate implementation, but I maintained an issue-by-issue specification, acceptance criteria and automated validation. I can explain the architecture, algorithms, failure modes and code decisions.

Then be prepared to write/modify Java without relying on the agent.

## 22. Interview prep requirement

Before putting LaunchForge prominently on a resume, personally be able to:

- implement a Spring REST endpoint;
- explain dependency injection;
- write Java collections/concurrency code;
- explain transactions;
- write SQL;
- explain Kafka partitions/consumer groups;
- explain Redis cache tradeoffs;
- code the rollout function;
- debug an SDK evaluation test;
- explain every major component in the architecture.
