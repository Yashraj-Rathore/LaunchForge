# 11 - Observability and Operations

## 1. Objectives

Operators need to answer:

- Did the publish commit?
- Was the revision propagated?
- What revision is each edge serving?
- Are SDK streams healthy?
- Is Kafka lagging?
- Is Redis being bypassed?
- Are authentication failures spiking?
- Did analytics degrade without affecting config?
- What changed before an incident?

## 2. OpenTelemetry

Instrument:

- management HTTP;
- config edge HTTP;
- publish transaction;
- outbox dispatch;
- Kafka producer/consumer;
- projection;
- Redis access;
- snapshot fallback to PostgreSQL;
- analytics ingestion.

Use W3C trace context where transport supports it.

Do not propagate arbitrary untrusted trace baggage into logs/metrics.

## 3. Correlation

Generate/accept a bounded correlation ID at ingress.

Include:

- trace ID;
- correlation ID;
- safe organization/environment opaque identifier when needed;
- operation;
- outcome.

Do not include flag values/context attributes as high-cardinality metric labels.

## 4. Metrics

### Management

- request count/latency/status;
- publish count/outcome;
- publish transaction latency;
- conflict count;
- auth denial count;
- key rotation/revocation count.

### Outbox/Kafka

- pending outbox rows;
- oldest pending age;
- publish retries;
- Kafka producer errors;
- consumer lag;
- projection errors;
- stale/duplicate events.

M7 exposes bounded custom meters through the Event Worker actuator:

- `launchforge.outbox.pending` and `launchforge.outbox.oldest.age.seconds` gauges;
- `launchforge.outbox.publish{outcome=published|retry|failed}`;
- `launchforge.projection{outcome=advanced|ignored|error}`.

Standard Kafka client metrics supply producer errors/latency and consumer lag. Metric labels never
contain organization, environment, event, key, or subject identifiers.

### Edge

- snapshot request count;
- p50/p95/p99 latency;
- 304 rate;
- Redis hit/miss/fallback rate;
- active SSE connections;
- reconnects;
- stream authentication denials;
- served revision watermark.

M7 adds `launchforge.edge.snapshot.cache{outcome=hit|miss|error}`,
`launchforge.edge.snapshot.fallback{outcome=read|rejected}`, and
`launchforge.edge.revision.hint{outcome=accepted|rejected}`. Snapshot response headers and each
edge's current-revision lookup provide the per-node revision diagnostic used by the two-edge drill;
no anonymous cross-tenant diagnostic route is introduced.

### SDK (local or opt-in telemetry)

The SDK should expose local diagnostics but not phone home by default.

Possible local counters:

- evaluation count by reason;
- snapshot update success/failure;
- stream state;
- current revision;
- LKG age.

### Analytics

- accepted/rejected batches;
- queue depth;
- ClickHouse insert latency/failure;
- dropped optional events.

## 5. Logs

Use structured JSON in deployed environments.

Examples of safe event names:

```text
ConfigRevisionPublished
OutboxDispatchSucceeded
ProjectionAdvanced
SnapshotServed
SdkKeyRejected
StreamConnected
StreamDisconnected
AnalyticsBatchDropped
```

Log IDs/fingerprints, not credentials.

## 6. Traces

Important trace:

```text
POST /publish
  -> PostgreSQL transaction
  -> outbox persisted

async trace/link:
outbox poll
  -> Kafka publish
  -> projection consumer
  -> Redis write
```

SDK delivery is decoupled. Publish-to-convergence is measured using revision timestamps/metrics, not one giant synchronous trace.

## 7. Health endpoints

Management API:

- `/actuator/health/liveness`;
- `/actuator/health/readiness`.

Readiness may depend on PostgreSQL, but should not require optional analytics.

Config Edge readiness:

- process healthy;
- key/auth config loaded;
- can resolve current snapshots using at least one approved source.

Be cautious about making readiness depend directly on Kafka: edge should continue serving last materialized/current configuration during broker outage.

## 8. Alerts

Candidate alerts:

- oldest outbox event over threshold;
- Kafka consumer lag sustained;
- projection revision behind DB current by threshold;
- Redis fallback rate high;
- edge 5xx elevated;
- SDK key auth failures anomalous;
- snapshot latency elevated;
- no published revision visible after bounded time;
- analytics failures sustained;
- PostgreSQL saturation.

Thresholds are tuned from measurements, not invented.

## 9. Dashboards

Create Grafana dashboards for:

1. control plane;
2. distribution plane;
3. config edge;
4. optional analytics;
5. release/deployment.

A recruiter screenshot should emphasize meaningful system state, not dozens of decorative panels.

## 10. Operational invariants

- management publish can succeed while Kafka is temporarily unavailable;
- outbox age exposes delayed distribution;
- edge can serve during Kafka outage;
- Redis is rebuildable;
- optional analytics cannot make evaluation/config unavailable;
- rollback creates a traceable new revision;
- all production config changes have audit identity.

## 11. Runbooks

Detailed failure steps live in `docs/18_FAILURE_MODES_RUNBOOKS.md`.

Required runbooks:

- Kafka unavailable;
- outbox backlog;
- projector lag;
- Redis outage/flush;
- edge elevated errors;
- PostgreSQL incident;
- key compromise;
- bad production flag/revision;
- analytics outage;
- release rollback.

## 12. Revision diagnostics endpoint

An authenticated internal/admin diagnostic can expose:

```json
{
  "environment": "production",
  "databaseRevision": 45,
  "redisRevision": 45,
  "edgeRevision": 45,
  "outboxOldestAgeMs": 0
}
```

Do not expose organization internals anonymously.

## 13. Audit vs logs

Audit answers "who changed what."

Logs answer "what did the system do."

Do not substitute one for the other.

Audit is durable product data. Logs have operational retention and redaction policy.

## 14. On-call simulation

For portfolio hardening, run documented drills:

- stop Kafka;
- publish revision;
- verify outbox backlog;
- restart Kafka;
- verify convergence;
- flush Redis;
- verify rebuild;
- kill edge pod;
- verify SDK reconnect/LKG;
- publish bad-but-valid demo flag then rollback.

Capture timestamps and results in a fictional reliability report.

The first automated M7 drill and its actual outcomes are recorded in
`docs/18_FAILURE_MODES_RUNBOOKS.md`. No availability or latency SLO is inferred from that local
functional evidence.

## 15. Cost awareness

Observability can become expensive.

Control:

- metric cardinality;
- log volume;
- trace sampling;
- analytics retention;
- debug logging duration.

Never label metrics with raw user IDs, flag keys at very high cardinality, emails, or SDK secrets.
