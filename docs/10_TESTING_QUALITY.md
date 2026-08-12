# 10 - Testing and Quality Strategy

## 1. Philosophy

The value of this project is not the number of components. It is the evidence that the components behave correctly under concurrency, partial failure, version skew, and tenant boundaries.

Tests are part of the architecture.

## 2. Test pyramid

### Domain unit tests

Fast tests for:

- flag type invariants;
- variation uniqueness;
- rule validation;
- rollout allocation;
- publish state;
- rollback semantics;
- role/policy decisions.

### Evaluator unit tests

Exhaustive deterministic behavior:

- matching/non-matching conditions;
- rule order;
- operators;
- missing attributes;
- type mismatch;
- fallthrough;
- disabled flag;
- rollout buckets.

### Golden compatibility tests

Same JSON fixtures consumed by:

- Java evaluator;
- JavaScript evaluator.

These are required CI gates.

### Application tests

Use mocked ports for:

- create/update/publish workflows;
- authorization;
- conflict handling;
- key lifecycle;
- outbox creation.

### PostgreSQL integration tests

Use Testcontainers.

Cover:

- migrations;
- constraints;
- tenant isolation;
- monotonic revision allocation under concurrency;
- optimistic concurrency;
- publish + audit + outbox transaction atomicity;
- duplicate key constraints.

Do not substitute H2 for PostgreSQL behavior.

The M1 integration suite runs the real V1 Flyway migration against PostgreSQL 18 and proves server-derived organization scope, cross-tenant read/write denial, CSRF/unknown-field rejection, safe denial audits, concurrent final-Owner protection, OAuth state/nonce/PKCE parameters, and absolute session expiry.

### Kafka/Redis integration tests

Later milestones use Testcontainers or a Compose integration environment.

Cover:

- outbox publication;
- duplicate event handling;
- ordering by environment;
- projector restart;
- Redis rebuild;
- stale revision ignored.

### API contract tests

Validate:

- OpenAPI;
- JSON schema;
- status/error envelope;
- SDK snapshot schema;
- SSE format;
- analytics contract.

### Browser E2E

Playwright for critical operator workflows.

The M1 Chromium smoke uses the real local Keycloak reference and seeded Control API. It proves login, permitted organization/role display, HttpOnly SameSite session cookies, empty browser token storage, cross-tenant URL denial, logout invalidation, and post-logout `401`.

### SDK integration

Run Java/JS SDKs against a real local Config Edge.

The M4 default suite covers credential formatting/verifier behavior, invalid/revoked/expired/
disabled/inactive authentication, management-cookie denial, canonical snapshot integrity and the
1 MiB response bound, ETag/304 headers, revision-only SSE parsing, heartbeat/connection lifecycle,
bounded connection quotas, stale events, repeated disconnect backoff, polling convergence, corrupt
snapshot retention, simulated network interruption/edge restart, and SDK shutdown/LKG behavior.
The PostgreSQL integration profile adds Flyway V3 key lifecycle/tenant tests and a real WebFlux
edge-to-Java-SDK kill-switch convergence flow.

The M5 suites execute the same frozen evaluator corpus in Java and TypeScript, then cover strict
Unicode/duplicate-property rejection, browser bootstrap and local evaluation, malformed/stale
last-known-good retention, SSE-triggered authoritative refresh, React subscription cleanup/context
replacement, public-key/server-key route separation, exact non-credentialed CORS, server-only flag
filtering before browser checksum generation, and Flyway V4 browser-key persistence. The Northstar
Playwright flow selects two deterministic fictional users and observes an SSE-driven kill switch
without an application redeploy.

The M6 suites add strict TypeScript form-contract tests for typed values, operator compatibility,
rule ordering, context attribute syntax, and exact rollout totals. React tests cover anonymous
login, server-authorized production context, and stale-write guidance. The isolated Playwright
control-API harness proves typed flag creation, local-edit preservation after conflict, rule save,
Java-backed simulation result rendering, production confirmation/reason, publish reconciliation,
one-time key secrets, key rotation/revocation, audit safety, and Viewer denial. PostgreSQL
integration coverage proves stable-ID variation updates, same-evaluator draft results, audit filters,
and cross-tenant denial for both simulator and audit routes.

### Performance

- JMH evaluator microbenchmarks;
- k6/Gatling HTTP load;
- SSE connection/reconnect profile;
- publish-to-visible convergence measurements.

## 3. Required test invariants

### Tenant isolation

For every new tenant-owned resource class, include a cross-tenant read/write denial test.

### Revision monotonicity

Concurrent publishes for the same environment cannot create duplicate or decreasing revision numbers.

### Immutability

Published revision content cannot be updated in place.

### Rollback

Rollback publishes a new revision whose content matches the selected historical source except for revision metadata.

### Evaluator determinism

Same inputs + same algorithm version + same snapshot -> same result across runs and languages.

### Availability

After bootstrap, local evaluation continues during Config Edge/Redis/Kafka outage.

### No partial snapshot

Concurrent evaluations during activation see either old complete snapshot or new complete snapshot.

## 4. Golden vector format

The LF-0302 canonical corpus is `contracts/golden-vectors/evaluator-v1.json`. Its version-1 shape includes `rolloutVectors`, `operatorCases`, one checksum-valid `evaluationSnapshot`, `evaluationCases`, `malformedSnapshots`, and `corpusChecksum`. The corpus checksum is SHA-256 over the RFC 8785 canonical projection with `corpusChecksum` absent.

Regenerate and verify it with:

```powershell
./mvnw.cmd -pl sdks/java/launchforge-java-sdk -am test "-Dtest=GoldenVectorCorpusTest" "-Dlaunchforge.updateGoldenVectors=true"
./mvnw.cmd -pl sdks/java/launchforge-java-sdk -am test "-Dtest=GoldenVectorCorpusTest"
corepack pnpm --filter @launchforge/js-core test
```

The first command computes SHA-256 expectations through the test reference implementation and
freezes the file. The second byte-compares the regenerated form with the committed artifact and
executes every Java case. The third runs the TypeScript evaluator directly against that same file.
CI's `evaluator-compatibility` job runs the Java verification and TypeScript corpus gate together.

Do not manually invent expected cryptographic hash results.

## 5. Mutation/property testing

Strong candidates:

- percentage allocations always map to exactly one variation when total is 100000;
- arbitrary valid rule order is deterministic;
- snapshot serialization round-trips;
- invalid type/operator combinations never evaluate as a match;
- random tenant IDs never bypass scoping.

A property-testing library may be added only when the benefit justifies dependency cost.

## 6. Architecture tests

Use ArchUnit to enforce:

- domain has no Spring/JPA dependency;
- application does not depend on adapters;
- management/web layers cannot bypass application boundaries;
- SDK modules do not depend on management server code;
- evaluator core has no network dependencies.

## 7. Static quality

Backend:

- Java compiler warnings policy;
- Spotless formatter;
- focused Checkstyle rules plus compiler `-Xlint:all`; add PMD, Error Prone, or SpotBugs later only for a demonstrated non-overlapping need;
- Maven Enforcer;
- dependency convergence checks.

Frontend:

- TypeScript strict;
- ESLint;
- formatting;
- no implicit `any`;
- test/lint/build CI.

Do not stack redundant analyzers that create noise.

## 8. Test data

Use fictional organizations/users.

Do not commit:

- production credentials;
- personal phone/email data;
- real customer configuration.

Stable seeds should support deterministic screenshots and demo videos.

## 9. Failure injection tests

At minimum:

- Postgres unavailable during publish;
- Kafka unavailable after DB commit;
- projector killed mid-message;
- Redis flushed;
- edge restarted with active SDK clients;
- SSE disconnected repeatedly;
- corrupt snapshot returned/injected;
- revoked key while stream is active;
- old revision replayed.

For each, document expected availability and recovery.

## 10. Concurrency tests

Key cases:

- two publishers editing same draft;
- publish vs rollback;
- multiple outbox publishers;
- multiple projectors;
- snapshot read during Redis rebuild;
- SDK evaluation during snapshot swap;
- concurrent Java client shutdown/update.

Avoid timing-only sleeps where synchronization primitives/await conditions can make tests deterministic.

## 11. Performance regression policy

Benchmarks are not necessarily blocking on every PR initially.

Before portfolio release:

- establish a saved baseline;
- document hardware/environment;
- compare evaluator throughput/latency;
- compare snapshot endpoint p95/p99;
- record SSE connection test.

Only make resume claims from reproducible tagged benchmark artifacts.

## 12. CI tiers

### PR fast gate

- formatting;
- static analysis;
- unit;
- golden vectors;
- architecture tests;
- frontend tests;
- build;
- Compose/config validation where cheap.

### PR integration gate

- PostgreSQL Testcontainers;
- selected Kafka/Redis integration;
- API contract;
- browser smoke.

### Release gate

- complete integration/E2E;
- container scan;
- SBOM/provenance;
- staging smoke;
- optional performance threshold.

## 13. Coverage

Do not optimize for one global percentage.

Require high confidence in critical code:

- evaluator;
- tenant authorization;
- publish transaction;
- key authentication;
- revision distribution.

Coverage reports are diagnostic, not a substitute for meaningful assertions.

## 14. Flaky tests

No "retry until green" policy.

When a test flakes:

1. preserve evidence;
2. identify timing/resource/root cause;
3. fix synchronization/isolation;
4. quarantine only with an issue and owner;
5. restore gate quickly.

## 15. Definition of done linkage

Every backlog issue lists its acceptance criteria. Completion requires `templates/definition-of-done.md`, including:

- tests;
- validation commands;
- documentation;
- security/privacy consideration;
- no unrelated next-milestone work.
