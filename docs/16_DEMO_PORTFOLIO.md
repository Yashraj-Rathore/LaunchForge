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

## 4. Deliberate recruiter demo (default, about four minutes)

The default live walkthrough is intentionally slower than the minimum two-minute acceptance path.
Speak before clicking, change one concept at a time, and leave the resulting visitor, reason,
bucket, and revision visible for at least four seconds. The pacing is presentation-only; do not add
latency to publication or SDK activation.

### 0:00-0:35 - Problem and safety model

Show the running Northstar storefront and say:

> LaunchForge changes feature behavior without redeploying. The SDK evaluates from an immutable
> local snapshot, so an application request does not call LaunchForge.

Pause on the visible Development revision and local evaluation diagnostics.

### 0:35-1:20 - Ordered targeting

Select **Maya / Canada Pro**, then **Alex / US Free**. Open `new-checkout` in the console and show:

```text
country == CA
AND plan == pro
-> ON
```

Pause after each visitor. Explain `RULE_MATCH` first, then `ROLLOUT_MATCH`; do not switch users while
still explaining the prior result.

### 1:20-2:10 - Deterministic rollout

Select **Ivy / Rollout cohort**. Her stable bucket is `29240`, so she is outside the initial 10%
cohort. Run the server-backed simulator with the same fictional context, then edit the allocation
from 10%/90% to 50%/50%. Emphasize that the bucket does not change; only the cumulative boundary
moves.

### 2:10-2:55 - Publish and live activation

Save the draft, review the diff, and publish revision 2. Keep the connected storefront visible
until Ivy changes to Express checkout and its revision advances. Pause on both values before moving
on. The revision-only SSE message caused an authoritative snapshot fetch; it did not carry a delta
or evaluation result.

### 2:55-3:30 - Kill switch

Disable `new-checkout`, save, review, and publish revision 3. Wait for the connected storefront to
show Classic checkout, `FLAG_DISABLED`, and revision 3. State explicitly that the application was
not rebuilt or redeployed.

### 3:30-4:15 - Engineering evidence

Show immutable revision history and the audit reason, then the architecture diagram. Mention:

- PostgreSQL revision plus transactional outbox;
- Kafka/Redis distribution with PostgreSQL authority;
- SSE revision hints and local Java/TypeScript evaluation;
- the shared golden corpus and failure/LKG evidence; and
- only the measured JMH results and limitations in `docs/23_RELIABILITY_PERFORMANCE_REPORT.md`.

End before opening unrelated screens. Let the interviewer choose the deeper station.

### Condensed two-minute acceptance path

The same real flow can be compressed without skipping evidence:

```text
0:00-0:20  problem + local evaluation
0:20-0:45  Maya targeting versus Alex rollout
0:45-1:05  Ivy bucket 29240; expand 10% -> 50%
1:05-1:25  publish; revision and connected storefront update
1:25-1:40  kill switch; FLAG_DISABLED at newer revision
1:40-2:00  immutable history/audit + architecture
```

Use the four-minute version by default. Use this condensed track only when the meeting format
actually imposes a two-minute limit.

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

Real-system media is generated into `demos/demo-media/`:

- `01-flag-workspace.png` - seeded flag list and environment context;
- `02-targeting-and-rollout.png` - ordered targeting and rollout editor;
- `03-deterministic-simulator.png` - exact fictional bucket result;
- `04-storefront-ten-percent.png` - Ivy outside the initial cohort;
- `05-live-rollout-update.png` - connected storefront after revision 2;
- `06-kill-switch.png` - safe disabled result at revision 3;
- `07-immutable-revisions.png` - monotonic revision history;
- `08-audit-trail.png` - durable human reasons;
- `launchforge-admin-tour.webm` - deliberate console portion; and
- `northstar-live-update.webm` - connected storefront portion.

The capture spec logs into the real Compose control plane, uses the real database/Edge/browser SDK,
publishes real immutable revisions, and records each browser page. It does not substitute the
in-process demo harness or edit evaluation behavior. The default `4000` millisecond pause makes the
recording readable; values from `1000` through `5000` are supported:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File eng/capture_demo_media.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File eng/capture_demo_media.ps1 -StepDelayMs 5000
```

The wrapper builds a pinned Node 24/Playwright capture image and runs it with host networking; the
host does not need a matching Node installation. Docker Desktop host networking must be enabled.

## 11. Demo reproducibility

Start or restore the complete deterministic demo with one top-level script:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File eng/demo.ps1 -Action Start
powershell -NoProfile -ExecutionPolicy Bypass -File eng/demo.ps1 -Action Reset -ConfirmReset
```

The script:

- creates fresh local-only ignored configuration when absent;
- starts the isolated Identity, Distribution, Platform, and Demo Compose profiles;
- enforces migration-before-workload ordering;
- seeds the generated fictional organization, three environments, four typed flags, contexts,
  browser key, revision, audit, and outbox event;
- starts the real React storefront;
- verifies service health, exact database seed counts, and a browser-projected Edge snapshot; and
- prints URLs and the generated local login.

The reset action removes only the `launchforge-demo` Compose project's containers and named volumes,
then rebuilds the same deterministic baseline. It requires the explicit `-ConfirmReset` switch.

## 12. Case study

The case study should explain at least three tradeoffs:

1. local SDK evaluation vs remote per-request evaluation;
2. full snapshots + revision notification vs delta streaming;
3. outbox/Kafka/Redis introduced after correctness, not from day one.

That shows engineering judgment rather than technology collection.
