# 16 - Demo and Portfolio Plan

## 1. Purpose

A recruiter should understand the value in 30 seconds; an engineer should find enough depth for a 30-minute architecture discussion.

## 2. Demo story

Fictional company: **Northstar Commerce**.

Project:

```text
storefront
```

Environments:

```text
development
staging
production
```

Flags:

- `new-checkout` - boolean;
- `search-ranking` - string variant;
- `recommendation-card` - JSON configuration;
- `fraud-review-threshold` - number (server-only demo).

## 3. Demo users

Use deterministic fictional contexts:

```text
canada-pro-user
us-free-user
internal-tester
anonymous-user
```

The seed stores only fictional attributes.

## 4. Two-minute recruiter demo

### 0:00-0:20 - Problem

Show a running demo storefront and explain:

> LaunchForge lets teams change feature behavior safely without redeploying. The SDK evaluates flags locally so application requests do not depend on LaunchForge.

### 0:20-0:45 - Targeting

Open `new-checkout`.

Rule:

```text
country == CA
AND plan == pro
-> ON
```

Show Canadian Pro user receives new checkout while US Free user does not.

### 0:45-1:05 - Percentage rollout

Change a rollout from 10% to 50%.

Use the simulator to demonstrate deterministic bucketing.

Explain that the same user remains in the same bucket.

### 1:05-1:25 - Live publish

Publish.

Show:

- revision increments;
- distribution status catches up;
- connected demo updates without application redeploy.

### 1:25-1:40 - Kill switch

Disable the flag and publish.

Show immediate safe fallback behavior.

### 1:40-2:00 - Engineering depth

Quickly show:

- architecture diagram;
- immutable revision history/audit;
- Java SDK;
- Kafka/Redis/edge metrics;
- benchmark link once real.

## 5. Engineering interview demo

Have optional deeper stations:

1. evaluator source/golden vectors;
2. publish transaction/outbox;
3. Kafka partitioning;
4. Redis rebuild;
5. SSE reconnect;
6. Java SDK LKG outage test;
7. Testcontainers;
8. ArchUnit;
9. JMH;
10. Helm/CI.

## 6. README structure after implementation

Recommended public README:

```text
Hero: what LaunchForge is
Animated/static demo
Why it exists
Architecture diagram
Key engineering highlights
Quick start
Java SDK example
React SDK example
Failure/reliability proof
Benchmarks
Security
Repository map
Tradeoffs
Roadmap
```

Do not lead with installation before explaining the product.

## 7. Evidence checklist

Before claiming a feature in README/resume:

- source code exists;
- automated tests exist where applicable;
- demo works from clean setup;
- docs match implementation;
- benchmark numbers have artifacts;
- cloud/Kubernetes claims were actually exercised if stated.

## 8. Potential resume bullets

These are **templates**, not ready-to-use claims until milestones are complete.

> Built a multi-tenant feature-management platform with Java/Spring Boot and React, implementing immutable configuration revisions, deterministic targeting, role-based control, and audited rollback.

> Developed a pure-Java local-evaluation SDK with cross-language golden compatibility tests, streaming configuration refresh, polling fallback, and last-known-good behavior.

> Implemented durable configuration propagation using a PostgreSQL transactional outbox, Kafka, Redis materialization, and horizontally scalable WebFlux/SSE edge nodes.

> Benchmarked [measured result only] using JMH and [load tool], with reproducible methodology and raw artifacts.

## 9. Portfolio differentiation

The project should communicate these distinct skills:

- modern Java/Spring;
- library/SDK design;
- deterministic algorithms;
- distributed consistency;
- real-time streaming;
- multi-tenancy/security;
- React product UX;
- performance/reliability measurement.

It should not be framed as "another SaaS CRUD dashboard."

## 10. Screenshots

Capture:

- flag list;
- rule builder;
- rollout simulator;
- publish review;
- revision history;
- SDK key page with fake/redacted key;
- live demo split screen;
- Grafana distribution dashboard.

## 11. Demo reproducibility

Provide one top-level command/script that:

- starts required Compose profile;
- migrates;
- seeds fictional data;
- prints URLs/users;
- verifies health.

A separate reset command restores deterministic state.

## 12. Case study

The case study should explain at least three tradeoffs:

1. local SDK evaluation vs remote per-request evaluation;
2. full snapshots + revision notification vs delta streaming;
3. outbox/Kafka/Redis introduced after correctness, not from day one.

That shows engineering judgment rather than technology collection.
