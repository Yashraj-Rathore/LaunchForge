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

---

## Runbook L - Analytics/ClickHouse outage

Expected:

- config/publish/evaluation unaffected;
- optional analytics can be dropped/buffered within bounded policy.

Actions:

1. verify isolation;
2. inspect bounded queue/backpressure;
3. restore ClickHouse;
4. resume inserts;
5. accept/document event gap if drop policy activated;
6. never slow config edge to preserve analytics.

---

## Runbook M - Failed deployment

1. compare Git SHA/image digest;
2. check migration compatibility;
3. if application regression, promote previous compatible digest;
4. do not reverse DB migrations automatically;
5. verify management/edge/projector smoke;
6. verify published revision unchanged;
7. document cause.

---

## Runbook N - Lost/stale operator session

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

## 3. Destructive action warning

Never:

- delete revision history to "fix" current state;
- clear outbox to reduce backlog;
- force Kafka offsets forward without accounting for missed revisions;
- flush Redis in production without rebuild plan;
- reset production DB volumes;
- print secrets for debugging.
