# 18 - Failure Modes and Runbooks

## 1. Operating principle

Feature evaluation should degrade toward the SDK's last-known-good configuration or caller fallback, not toward synchronous dependency on the control plane.

Every runbook begins with diagnosis, protects data/config history, and avoids destructive recovery unless necessary.

---

## Runbook A - Kafka unavailable

### Symptoms

- outbox oldest age increasing;
- Kafka producer errors;
- published DB revision ahead of projected/edge revision.

### Expected product behavior

- management publish transaction can commit;
- outbox row remains pending;
- existing edge snapshots continue;
- SDK local evaluations continue;
- new revision distribution is delayed.

### Actions

1. verify PostgreSQL revision/outbox state;
2. verify broker health/network/DNS/auth;
3. do not delete pending outbox rows;
4. restore broker;
5. observe publisher retries;
6. verify projector advances in order;
7. verify Redis/edge/SDK revision catches up;
8. document delay.

---

## Runbook B - Outbox backlog

### Symptoms

- oldest pending age elevated;
- pending count growing;
- Kafka may be healthy.

### Actions

1. inspect publisher health/logs;
2. check lease/stuck rows;
3. check Kafka ack latency/errors;
4. ensure only safe idempotent publisher behavior;
5. restart publisher if appropriate;
6. never mark rows published manually without broker evidence;
7. verify gap-free revision projection.

---

## Runbook C - Projector lag/failure

### Expected behavior

- Kafka retains revision events;
- Redis may remain at older valid snapshot;
- SDK keeps LKG/current old revision.

### Actions

1. inspect consumer lag and errors;
2. identify poison/schema event;
3. do not skip an event silently;
4. correct compatible consumer/contract issue;
5. replay/resume;
6. verify monotonic projection;
7. compare DB/Kafka/Redis revision watermarks.

---

## Runbook D - Redis unavailable

### Expected behavior

- edge uses bounded PostgreSQL fallback when configured;
- latency may rise;
- SDKs retain LKG;
- management DB remains authoritative.

### Actions

1. verify Redis connectivity;
2. confirm DB fallback rate/circuit protection;
3. avoid connection storm to PostgreSQL;
4. restore Redis;
5. rebuild current snapshots;
6. verify cache checksums/revisions;
7. clear alert after stable hit rate.

---

## Runbook E - Redis flushed/stale

1. stop any process writing known-bad materialization if required;
2. rebuild from latest immutable published revisions;
3. projector can replay/current-load;
4. compare revision/checksum against PostgreSQL;
5. verify edges;
6. never reconstruct revision history from Redis.

---

## Runbook F - Config Edge elevated errors

1. identify whether auth, Redis, DB, CPU/memory or deployment issue;
2. keep healthy replicas serving;
3. drain/restart bad replica if appropriate;
4. SDKs should continue LKG;
5. verify reconnect jitter prevents storm;
6. roll back application image if newly introduced;
7. do not roll back configuration unless configuration itself is bad.

---

## Runbook G - PostgreSQL unavailable

### Impact

Management writes/publishes unavailable. Edge may serve Redis materialized snapshots temporarily. SDK local evaluation continues.

### Actions

1. stop repeated migration/write retries from causing overload;
2. verify managed DB status;
3. protect Redis current materialization from accidental clearing;
4. restore DB/service;
5. verify revision pointers/outbox integrity;
6. resume writes;
7. reconcile Redis/current revision.

Do not claim full platform availability during DB outage.

---

## Runbook H - Compromised SDK key

1. identify key fingerprint/environment;
2. revoke immediately;
3. rotate/create replacement;
4. verify new key;
5. terminate/deny old stream and snapshot access within documented bound;
6. inspect audit/auth logs for suspicious usage;
7. notify affected pilot/customer as required;
8. never post leaked key in issue/chat/log.

---

## Runbook I - Bad production flag configuration

1. identify current revision and user impact;
2. use kill switch/edit or select known-good historical revision;
3. publish corrective **new** revision with reason;
4. watch projection/edge/SDK convergence;
5. verify demo/health/business behavior;
6. preserve bad revision for audit;
7. write incident learning.

Do not mutate/delete the bad historical revision.

---

## Runbook J - Corrupt snapshot detected by SDK

Expected SDK behavior:

- reject candidate;
- retain previous valid snapshot;
- emit bounded diagnostic;
- continue evaluation.

Actions:

1. capture revision/checksum/schema metadata, not secrets;
2. compare edge response with authoritative revision;
3. identify serialization/projection defect;
4. stop propagating corruption if ongoing;
5. correct server and publish/rebuild as appropriate;
6. verify SDK accepts valid newer revision.

---

## Runbook K - SSE reconnect storm

1. inspect recent edge rollout/network event;
2. verify client exponential backoff+jitter;
3. rate limit new connections carefully;
4. scale healthy edge capacity if proven resource issue;
5. allow polling/LKG fallback;
6. avoid broadcasting artificial reconnect commands;
7. reproduce with load test before tuning.

The cluster-wide Redis lease rejects connections above configured global/per-key bounds. During
Redis loss, per-process bounds remain active; aggregate cluster admission can therefore be higher
than the Redis-backed ceiling. Treat the `local_fallback` signal as degraded protection, avoid
scaling out solely to absorb abusive clients, and restore Redis before raising limits.

---

## Runbook L - Governed audit retention

1. confirm the organization's documented retention obligation and approval;
2. keep `LAUNCHFORGE_AUDIT_RETENTION_DELETION_ENABLED=false` while reviewing;
3. request a cutoff/limit preview as an Owner/Admin and export any required archive;
4. verify the exact candidate count, organization, cutoff, and 15-minute expiry;
5. enable deletion only for the approved maintenance window;
6. apply with the exact count; a mismatch/expiry must return `409` and delete nothing;
7. verify the new `AUDIT_RETENTION_APPLIED` event and tenant counts;
8. disable deletion again and retain the maintenance evidence.

Never issue direct SQL update/delete against `audit_events`; the database trigger rejects it.

---

## Runbook M - Analytics/ClickHouse outage

Expected:

- config/publish/evaluation unaffected;
- Java/browser SDKs keep returning the local evaluation result and may drop when their finite queue
  fills or a batch request fails;
- Config Edge sheds analytics independently at its concurrency/per-key limits and returns `429`, or
  `503` when the dedicated Kafka publication fails;
- Event Worker drains only finite insert batches and drops a failed batch instead of retrying
  indefinitely or blocking its configuration projector;
- Control API returns isolated `429`/`503` analytics errors while management and configuration APIs
  remain available.

Actions:

1. verify snapshot reads, publish, rollback, SSE/polling convergence, and local evaluation remain
   healthy before investigating telemetry;
2. inspect `launchforge.analytics.*` ingestion, queue, worker, insert-duration, query, and drop
   signals without adding tenant/flag/subject labels;
3. confirm the dedicated analytics Kafka topic is available and consumer lag is bounded;
4. restore ClickHouse and verify `/ping` plus a bounded aggregate query;
5. verify new inserts resume; do not replay SDK-local dropped events or introduce an unbounded
   recovery queue;
6. record the event-gap interval and whether ingestion, worker, or SDK drop policy activated;
7. never slow Config Edge snapshot/stream work or SDK evaluation to preserve analytics.

The M8 worker unit test forces a ClickHouse insert failure and proves the batch is counted/dropped
without escaping the scheduled flush. `AnalyticsClickHouseIT` starts the pinned real image and
proves batched writes, duplicate-tolerant `uniqExact(event_id)` aggregation, the 90-day TTL, and the
absence of subject/context columns.

---

## Runbook N - Failed deployment

1. stop further promotion and capture the failing Release/Promote run ID, tag, Git SHA, deployed
   release-metadata ConfigMap, and all image digests;
2. validate the release manifest and compare its SHA/digests with the cluster; do not substitute a
   mutable tag;
3. check the current database schema against the candidate application's declared compatibility;
4. if the application regressed, run `Roll back production application` with an incident/change
   reference and a previous successful staging run/tag;
5. do not reverse Flyway migrations automatically; the rollback workflow disables the migration
   Job and blocks an incompatible candidate;
6. verify management, Edge snapshot, projector, SSE/SDK convergence, deployed SHA, and unchanged
   database schema;
7. verify the published configuration revision is unchanged. If configuration behavior must be
   restored, use product revision history to publish a newer rollback revision instead;
8. record approver, timeline, affected scope, evidence, and forward fix.

Manifest and attestation verification commands, required GitHub Environment protection, and the
distinction between application and configuration rollback are in
`docs/24_RELEASE_SUPPLY_CHAIN.md`.

---

## Runbook O - Lost/stale operator session

1. reauthenticate through OIDC;
2. do not retry an ambiguous publish blindly;
3. query current revision/draft concurrency state;
4. reconcile before new publish.

---

## 2. Reliability drill record template

For every exercise:

```text
Date:
Environment:
Git SHA:
Scenario:
Expected behavior:
Commands/actions:
Observed behavior:
Time to detection:
Time to recovery:
Data/config loss:
User-visible impact:
Gaps:
Follow-up issue IDs:
```

## 3. M7 local distribution drill - 2026-08-13

**Environment:** Local Testcontainers on Docker Desktop; PostgreSQL 18.4, Apache Kafka 4.3.1, and
Redis 8.2.8. The repository base was `22b0a27` plus the Prompt 8 working tree.

**Command:**

```powershell
.\mvnw.cmd -pl tests/integration-tests -am verify -Pintegration "-Dit.test=DistributionPipelineIT" "-Dfailsafe.failIfNoSpecifiedTests=false"
```

**Expected and observed:**

| Scenario | Expected | Observed |
|---|---|---|
| Two outbox workers claim one event | Only one live lease; an expired lease is recoverable | Worker B was denied while Worker A's lease was live, then claimed it after expiry |
| Permanent invalid outbox envelope | No blind transient retry | Row became `FAILED` with bounded code `OUTBOX_EVENT_INVALID` |
| Duplicate Kafka event | Materialized revision never regresses/repeats | Redis remained on revision 1 |
| Kafka paused during revision 2 publish | PostgreSQL commit/outbox survive, then catch up | Row remained unpublished with retry attempts; after unpause it became `PUBLISHED` and Redis/edge reached revision 2 |
| Projector stopped during revision 3 | Kafka retains work; prior valid state remains | Redis stayed on revision 2 and advanced to 3 after the listener restarted |
| Redis `FLUSHALL` | Current snapshots rebuild from authoritative data | Reconciliation restored revision 3 from PostgreSQL |
| Two Config Edge processes | Both serve the same current revision without affinity | Both independently reported revisions 1, 2, and PostgreSQL fallback revision 3 |
| Edge restart / SDK source loss | Restarted edge converges; SDK retains local evaluation | Restarted edge bootstrapped revision 2; Java SDK continued evaluating its last-known-good snapshot after its edge context closed |
| Redis paused | Controlled PostgreSQL fallback, no snapshot regression | Both edges returned revision 3 through the semaphore-bounded fallback |

The focused reactor completed with `BUILD SUCCESS`: one drill test, zero failures/errors. Detection
and recovery time were not benchmarked; test await bounds are safety timeouts, not latency or
availability claims. No revision, configuration, or evaluation state was lost. During Kafka or
projector interruption only the newer revision was delayed; already loaded SDK behavior remained
available. No follow-up correctness gap was found within LF-0701 through LF-0706. Capacity, SLO,
chaos-duration, and production alert-threshold evidence remains owned by M10.

## 4. M10 reliability drill - 2026-08-17

**Environment:** Windows kernel 10.0.22631 x64 on Docker Desktop; PostgreSQL 18.4, Apache Kafka
4.3.1, and Redis 8.2.8 Testcontainers. The exercised implementation commit was
`993980f2ee70b134c47d1590e07bd3cf9a86cd94`.

**Command:**

    .\mvnw.cmd -pl tests/integration-tests -am verify -Pintegration "-Dit.test=ControlPlanePostgresIT,DistributionPipelineIT" "-Dfailsafe.failIfNoSpecifiedTests=false"

**Expected and observed:**

| Scenario | Expected | Observed |
|---|---|---|
| Publish transaction and tenant diagnostic | PostgreSQL revision/outbox commit atomically; only the owning tenant can inspect bounded pipeline state | Owner saw database revision 1 with one pending row and `PENDING`; the other organization received `404` |
| Duplicate/stale delivery | Repeated or older Kafka work cannot regress Redis | Duplicate revision was ignored and the materialized revision remained monotonic |
| Kafka interruption | Publish remains durable in PostgreSQL/outbox and catches up after broker recovery | Pending work survived the pause, was acknowledged after unpause, and edge advanced |
| Projector interruption | Prior materialized revision remains readable; retained Kafka work catches up | Redis stayed on the prior revision and advanced when the listener restarted |
| Redis flush/outage | Authoritative state rebuilds; bounded PostgreSQL fallback preserves current reads | Reconciliation rebuilt Redis after flush; both edge instances served the current revision during pause |
| Edge restart / SDK source loss | Restarted edge converges; SDK keeps validated local state | Restarted edge converged and Java evaluation continued from LKG when the source context closed |
| Invalid outbox event | Permanent envelope defect is not retried blindly | Row became `FAILED` with bounded `OUTBOX_EVENT_INVALID` |
| Historical rollback | Recovery creates a higher immutable revision and retains history | Control-plane coverage restored the selected behavior as a newer revision |

The focused reactor completed with `BUILD SUCCESS`: 12 `ControlPlanePostgresIT` tests and one
`DistributionPipelineIT` drill, zero failures/errors. Test durations (12.26 seconds and 21.13
seconds) are harness durations, not detection, recovery, convergence, or availability
measurements. No configuration/revision loss was observed. No LF-1006 correctness gap remains;
production chaos duration, paging thresholds, and multi-host capacity remain future
environment-specific work.

## 5. Destructive action warning

Never:

- delete revision history to "fix" current state;
- clear outbox to reduce backlog;
- force Kafka offsets forward without accounting for missed revisions;
- flush Redis in production without rebuild plan;
- reset production DB volumes;
- print secrets for debugging.
