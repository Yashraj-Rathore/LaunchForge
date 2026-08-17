# 13 - Performance and Capacity Plan

## 1. Principle

Performance is valuable only when measured reproducibly.

This document defines hypotheses and experiments. It does not authorize invented benchmark claims.

## 2. Critical paths

### Hot path A: SDK evaluation

No network/database.

Inputs:

- immutable compiled snapshot;
- evaluation context;
- flag key.

Work:

- lookup;
- ordered conditions;
- deterministic hash if rollout;
- return typed variation/detail.

### Hot path B: snapshot fetch

```text
SDK -> Edge -> Redis -> response
```

Fallback:

```text
SDK -> Edge -> PostgreSQL -> materialize -> response
```

### Path C: publish convergence

```text
Management -> PostgreSQL/outbox -> Kafka -> projector -> Redis -> edge notification -> SDK fetch
```

### Path D: SSE fan-out

Long-lived connections plus small revision notifications.

### Path E: analytics

Explicitly lower priority than configuration distribution.

## 3. JMH plan

Benchmarks:

- boolean flag no rules;
- first-rule match;
- last-rule match;
- rollout;
- string/number/JSON variations;
- context with typical attributes;
- large but permitted rule set;
- snapshot swap under concurrent readers.

Record:

- JDK build;
- OS/CPU/RAM;
- warmup/forks;
- throughput;
- p50/p95 where meaningful;
- allocation rate.

Never compare Java/JS with incompatible benchmark setups.

## 4. HTTP load plan

Use k6 or Gatling.

Scenarios:

### Snapshot cached

- valid server SDK keys;
- mostly `304`;
- Redis warm.

### Snapshot cold

- Redis miss;
- DB fallback.

### Key authentication

- mixed valid/invalid keys;
- rate limits.

### Management

- lower volume;
- publish correctness under concurrency more important than raw RPS.

## 5. SSE load plan

Measure:

- 100/1k/5k+ concurrent connections as local resources allow;
- memory per connection;
- CPU idle/heartbeat;
- notification fan-out;
- reconnect storm;
- rolling edge restart;
- publish-to-client receive distribution.

Do not claim internet-scale capacity from a laptop.

## 6. Publish convergence experiment

For each test publish, record timestamps:

```text
t0 DB revision committed
t1 outbox Kafka acknowledged
t2 projector Redis updated
t3 edge observed revision
t4 Java SDK activated snapshot
t5 JS SDK activated snapshot
```

Compute distributions across repeated runs.

## 7. Capacity controls

- snapshot max size;
- max flags/environment;
- max rules/flag;
- max conditions/rule;
- context max attributes/bytes;
- SSE connections/key/IP;
- management mutation rate;
- analytics request max 256 KiB and batch max 100 events;
- SDK analytics queue/batch bounds and Event Worker queue/500-row insert bound;
- analytics ingestion/query concurrency and per-key request limits;
- Kafka consumer batch;
- Redis value max.

Initial limits should be conservative and configurable, with validation tests.

The evaluator/snapshot version-1 hard limits are normative in `docs/04_API_AND_CONTRACTS.md`. Rate, connection, Kafka batch, and Redis operational defaults are introduced and measured in their owning milestones; they must not weaken the contract hard limits.

## 8. Caching

### SDK

Primary cache: immutable in-memory snapshot.

### Edge

Redis current snapshot.

Optional tiny local edge cache may be evaluated, but it complicates invalidation. Add only if measured Redis latency/availability justifies it.

### Management

No need for aggressive caching initially.

## 9. Database indexes

Validate with actual queries.

Expected indexes:

- project/environment natural keys;
- flag key within project;
- revision by environment/revision number;
- current published pointer;
- outbox pending/order;
- SDK lookup ID;
- audit by organization/time.

Use `EXPLAIN (ANALYZE, BUFFERS)` in performance work, not index guessing alone.

## 10. Payload efficiency

Runtime snapshots should omit management data.

Potential optimizations only after baseline:

- gzip/brotli HTTP compression;
- compact JSON;
- precompiled representation;
- delta updates.

MVP deliberately uses full authoritative snapshots after revision notification because it is easier to reason about.

## 11. Failure/capacity interaction

A Redis outage can shift load to PostgreSQL. Prevent cascading failure with:

- bounded fallback concurrency;
- circuit breaker;
- LKG on clients;
- reasonable edge response behavior;
- alerting.

A reconnect storm can overload edge. Use exponential backoff/jitter and connection rate limiting.

M8 analytics uses independent finite queues, non-blocking SDK enqueue, bounded Kafka publication,
bounded ClickHouse insert batches/timeouts, and separate ingestion/query semaphores. When those
limits are exhausted, optional events or queries are shed; configuration work is not queued behind
analytics. Exact defaults live in each process's `application.yml` and `.env.example`.

## 12. Portfolio benchmark report

`demos/benchmark-report/` should eventually contain:

- tagged commit;
- environment;
- commands;
- raw result artifact;
- summarized table;
- charts generated from raw data;
- limitations.

Resume bullet must link to or be reproducible from this evidence.

M10 implements the executable JMH module and k6 suites under `tests/performance/`. The JMH jar
benchmarks boolean/default, matching-rule, permitted 100-rule worst-position, percentage rollout,
and JSON variation evaluation with the GC allocation profiler. The raw artifact and honest host
report are in `tests/performance/results/2026-08-17/` and
`docs/23_RELIABILITY_PERFORMANCE_REPORT.md`.

The k6 scripts cover conditional/cold snapshot reads, reconnecting SSE connection pressure, and
authenticated revision convergence polling. The committed M10 run validates script configuration
only; it does not claim HTTP capacity, SSE scale, or convergence latency. A controlled deployment
and completed `tests/performance/load-report-template.md` are mandatory before publishing such a
claim.

## 13. Example acceptable claim format

Only after measuring:

> Benchmarked local Java SDK evaluation at X operations/sec under documented JMH conditions and validated Y concurrent SSE connections with Z p95 notification latency on specified hardware.

Do not present a target as an achieved result.
