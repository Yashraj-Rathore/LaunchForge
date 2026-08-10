# 15 - Backlog, Stories, and Acceptance Criteria

## How to use this backlog

Codex implements one bounded issue at a time unless a prompt explicitly groups tightly coupled foundation issues.

Every issue must satisfy:

- its acceptance criteria;
- `templates/definition-of-done.md`;
- repository architecture/security rules;
- relevant tests;
- documentation updates.

Do not implement future milestone functionality "while already in the file."

---

# Epic E0 - Foundation

## LF-0001 - Create Java/Maven repository structure

**Story:** As a developer, I need controlled module boundaries so the platform remains reviewable and testable.

**Acceptance:**

- Maven wrapper committed and validated;
- root multi-module build created;
- modules reserved as `backend/launchforge-domain`, `backend/launchforge-application`, `backend/launchforge-contracts`, `backend/launchforge-infrastructure`, `backend/launchforge-control-api`, `backend/launchforge-config-edge`, `backend/launchforge-event-worker`, and the documented test modules;
- `sdks/java/launchforge-java-sdk` has its own clearly bounded module;
- `sdks/javascript/packages/{core,browser,react}` is reserved as one pnpm workspace with explicit package boundaries;
- React/frontend directories are reserved, not prematurely filled beyond Prompt scope;
- clean build succeeds;
- dependency direction documented.

## LF-0002 - Architecture and code-quality gates

**Acceptance:**

- ArchUnit tests enforce documented boundaries;
- Java formatter and focused static-analysis tools configured;
- compiler/static warnings policy documented;
- Maven Enforcer or equivalent protects Java/Maven/dependency assumptions;
- CI skeleton runs formatting/build/unit/architecture;
- analyzer choices do not duplicate each other excessively.

## LF-0003 - Local PostgreSQL

**Acceptance:**

- Docker Compose starts PostgreSQL;
- health check exists;
- credentials supplied through local environment/template;
- management application can establish a test connection;
- start/stop/reset commands documented;
- no real secret committed.

## LF-0004 - Frontend foundation

**Acceptance:**

- React + TypeScript strict + Vite workspace initialized;
- lint/test/build commands exist;
- only a minimal shell is created;
- no fake production flag screens are implemented yet;
- Node/npm versions/policy are documented.

## LF-0005 - Pin technology baseline and developer commands

**Acceptance:**

- Java/JDK, Spring Boot, React, PostgreSQL, Kafka, Redis and toolchain versions resolved from current official sources;
- choices recorded in `docs/19_TECHNOLOGY_BASELINE.md`;
- exact build/test/format commands work;
- `.tool-versions`, `.java-version`, Node version mechanism, or equivalent committed where useful;
- no dependency is added only for resume keyword value.

---

# Epic E1 - Tenancy and Identity

## LF-0101 - Organization and membership domain

**Acceptance:**

- Organization has stable ID/key/name/status;
- membership joins an identity subject to one organization and role;
- Owner/Admin/Developer/Viewer roles defined;
- lifecycle invariants tested;
- Domain has no Spring/JPA dependencies.

## LF-0102 - Organization-scoped persistence

**Acceptance:**

- Flyway baseline migration exists;
- tenant-owned records are organization scoped;
- repository/application APIs require server-derived organization context;
- cross-tenant read/write integration tests deny access;
- browser-supplied organization IDs cannot override authorization context.

## LF-0103 - OIDC BFF/session authentication

**Acceptance:**

- local Keycloak reference can authenticate an operator;
- Authorization Code/OIDC validation is correct;
- management browser receives secure HttpOnly same-origin session cookie;
- no long-lived token stored in localStorage;
- logout invalidates local session;
- unauthenticated requests fail correctly;
- CSRF foundation exists for mutations.

## LF-0104 - Role authorization policies

**Acceptance:**

- role abilities match security matrix;
- production publish policy is represented explicitly;
- API/application service policies are tested;
- UI hiding is not treated as authorization;
- audit captures relevant denied high-impact actions without secrets.

## LF-0105 - Seeded authenticated control-plane shell

**Acceptance:**

- fictional organization/project seed can be enabled locally;
- authenticated user sees only permitted organization data;
- React shell displays session/org;
- cross-tenant URL manipulation fails;
- Playwright login/access smoke passes.

---

# Epic E2 - Flag Management and Publishing

## LF-0201 - Projects and environments

**Acceptance:**

- project belongs to organization;
- project key unique within organization;
- environment key unique within project;
- Development/Staging/Production are seed examples, not hardcoded requirements;
- environment lifecycle/status modeled;
- concurrency version exists on mutable management state;
- tests/migrations included.

## LF-0202 - Typed flags and variations

**Acceptance:**

- BOOLEAN, STRING, NUMBER, JSON flag types supported;
- flag key unique within project;
- variation keys unique per flag;
- values validate against flag type;
- at least one valid variation/fallthrough required;
- published revision cannot reference invalid/missing variation;
- domain tests cover invariants.

## LF-0203 - Environment flag configuration/drafts

**Acceptance:**

- each environment can configure flag enabled state/rules/fallthrough/rollout independently;
- draft edits do not affect runtime published snapshot;
- optimistic concurrency rejects stale updates;
- tenant scope enforced;
- audit-safe change summary can be generated.

## LF-0204 - Rule model and validation

**Acceptance:**

- rules are ordered;
- conditions inside a rule use AND;
- supported operators are explicitly versioned/documented;
- operator allowed values depend on attribute/value type;
- no arbitrary code/regex;
- limits on rules/conditions/value count exist;
- malformed configuration cannot publish;
- unit tests cover operator/type matrix.

## LF-0205 - Percentage rollout configuration

**Acceptance:**

- allocation uses 100000 integer buckets;
- allocations total exactly 100000 when enabled;
- stable rollout salt modeled;
- subject attribute key explicitly chosen;
- changing salt requires deliberate operation/change summary;
- no floating-point allocation ambiguity;
- boundary tests included.

## LF-0206 - Publish immutable revision transaction

**Acceptance:**

- publish validates full environment draft;
- allocates monotonic revision number safely under concurrency;
- writes immutable revision, current-published pointer, audit record and outbox row atomically in PostgreSQL;
- failed transaction exposes no partial revision;
- published content cannot be mutated in place;
- PostgreSQL concurrency/integration tests included.

## LF-0207 - Revision history, diff and rollback semantics

**Acceptance:**

- revisions can be listed/read;
- safe structured diff to prior revision available;
- rollback selects historical content but publishes a **new higher revision**;
- rollback is audited with actor/reason/source revision;
- current revision never decreases;
- test proves rollback does not mutate history.

---

# Epic E3 - Reference Evaluator and Java SDK

## LF-0301 - Pure Java evaluator core

**Acceptance:**

- evaluator has no Spring/network/database dependencies;
- accepts compiled immutable snapshot + context + flag key;
- returns typed value/detail;
- implements disabled, rule, rollout and fallthrough behavior;
- caller default used on documented errors;
- deterministic unit tests cover every reason code.

## LF-0302 - Define algorithm version and golden vector corpus

**Acceptance:**

- `algorithmVersion=1` documented;
- fixtures cover all flag types/operators/errors/Unicode/boundaries;
- expected outputs generated/verified by reference implementation;
- fixtures are language-neutral JSON;
- fixture checksum/versioning documented;
- no hand-invented hash outputs.

## LF-0303 - Deterministic rollout hashing

**Acceptance:**

- exact material format is UTF-8 `<flagKey>\n<rolloutSalt>\n<subject>`;
- SHA-256 used;
- first 8 bytes interpreted unsigned big-endian;
- modulo 100000 selects bucket;
- cumulative allocation selects variation;
- missing subject attribute follows documented fallback behavior;
- golden vectors and boundary tests pass.

## LF-0304 - Java SDK bootstrap/authentication

**Acceptance:**

- SDK has builder/configuration API;
- SDK key accepted from caller configuration, never logged;
- snapshot endpoint fetched with bounded timeout;
- schema/version/checksum validated before activation;
- blocking/non-blocking bootstrap behavior documented/tested;
- SDK remains independent of Spring.

## LF-0305 - Java SDK local typed evaluation

**Acceptance:**

- boolean/string/number/JSON APIs;
- detail API includes bounded reason/revision metadata;
- no network/database on evaluation hot path;
- concurrent evaluation safe;
- snapshot held immutably/atomically;
- type mismatch returns caller fallback/detail, not unsafe coercion;
- tests include concurrent readers.

## LF-0306 - Polling and last-known-good memory behavior

**Acceptance:**

- conditional GET/ETag supported;
- jittered polling;
- transient remote failure retains active snapshot;
- invalid newer snapshot rejected while old remains active;
- shutdown idempotent;
- no reconnect/poll tight loop;
- outage tests pass.

## LF-0307 - Spring Boot demo integration

**Acceptance:**

- separate fictional demo Spring application uses published SDK artifact/project dependency;
- at least two flags demonstrate typed evaluation;
- app works when Config Edge is stopped after bootstrap;
- README includes commands;
- no LaunchForge server internals imported.

---

# Epic E4 - Config Edge and Live Updates

## LF-0401 - Config Edge service and SDK-key authentication

**Acceptance:**

- separate Spring Boot WebFlux deployable;
- server SDK key lookup uses non-secret lookup ID + constant-time verification;
- key maps to one permitted environment;
- revoked/expired/disabled key denied;
- management session cannot substitute for SDK key accidentally;
- authentication integration tests included.

## LF-0402 - Authoritative snapshot endpoint

**Acceptance:**

- versioned endpoint returns only runtime-safe snapshot;
- ETag and revision header;
- `If-None-Match` can return 304;
- checksum/schema version included;
- management/audit/secret data excluded;
- PostgreSQL-backed implementation works before Redis;
- request/response size limits tested.

## LF-0403 - Revision SSE stream

**Acceptance:**

- authenticated stream scoped to environment;
- revision notifications only, not full snapshot;
- heartbeat;
- duplicate/stale revision safe;
- active connection metrics;
- bounded connection limits;
- revoked key causes/forces eventual disconnect denial;
- integration tests parse valid SSE contract.

## LF-0404 - Java SDK streaming client

**Acceptance:**

- optional stream mode;
- revision event triggers conditional authoritative snapshot fetch;
- exponential reconnect with jitter;
- missed events converge to latest snapshot;
- polling fallback remains active/available;
- clean shutdown closes stream;
- no duplicate snapshot activation.

## LF-0405 - Edge/SDK failure-mode integration tests

**Acceptance:**

- edge restart;
- network interruption;
- stale event;
- corrupt snapshot;
- repeated stream disconnect;
- revoked key;
- SDK continues LKG where appropriate;
- tests use bounded timeouts and deterministic await conditions.

## LF-0406 - Live update demo

**Acceptance:**

- changing/publishing a flag visibly updates Java demo without application redeploy;
- kill-switch flow demonstrated;
- demo proves local evaluation still works during edge outage after bootstrap;
- instructions are reproducible and use fictional data.

---

# Epic E5 - JavaScript and React SDKs

## LF-0501 - TypeScript evaluator compatibility

**Acceptance:**

- TypeScript evaluator implements same semantic contract as Java;
- same golden vector corpus passes;
- no duplicated alternate algorithm;
- strict types;
- canonical numeric/JSON behavior documented where language differences matter.

## LF-0502 - JavaScript SDK bootstrap/poll/stream

**Acceptance:**

- environment-appropriate key/auth model;
- local evaluation;
- snapshot schema/checksum validation;
- polling + SSE;
- last-known-good memory;
- bounded reconnect;
- tests for malformed/stale snapshots.

## LF-0503 - Browser-safe client configuration

**Acceptance:**

- distinct client/mobile key type;
- browser endpoint refuses server-only snapshot content;
- client key cannot access management API;
- CORS rules explicit;
- documentation warns client keys/config are inspectable;
- no authorization use case relies only on client flag.

## LF-0504 - React SDK wrapper

**Acceptance:**

- provider owns one JS client;
- hooks for typed flag/detail;
- context updates documented;
- subscriptions cleaned up;
- rendering updates on snapshot activation;
- no evaluator duplicated in React package.

## LF-0505 - React demo app

**Acceptance:**

- fictional ecommerce/demo app uses React SDK;
- two deterministic users demonstrate targeting/rollout;
- live update/kill switch visible;
- no secret key in source;
- E2E demo flow included.

---

# Epic E6 - Admin Console

## LF-0601 - Console navigation and project/environment context

**Acceptance:**

- project/environment context always visible;
- flags/revisions/audit/keys routes;
- loading/empty/denied/error states;
- responsive accessible shell;
- tenant scope via API, not frontend filtering.

## LF-0602 - Flag and variation editor

**Acceptance:**

- create/edit flag metadata and typed variations;
- type validation;
- stale-write conflict UI;
- draft/published distinction obvious;
- production state visually distinct;
- browser tests.

## LF-0603 - Ordered rule builder

**Acceptance:**

- explicit attribute/operator/value controls;
- invalid type/operator combinations prevented;
- rule/condition reordering;
- no arbitrary expression input;
- accessible keyboard behavior;
- tests for serialized contract.

## LF-0604 - Rollout editor and evaluator simulator

**Acceptance:**

- integer allocations total 100%;
- stable salt and subject attribute visible;
- context simulator invokes `POST /api/v1/environments/{environmentId}/evaluate`, backed by the same pure Java evaluator and golden vectors as the SDK;
- shows matched reason/variation without implying future random outcomes;
- salt-change warning.

## LF-0605 - Publish, revisions, diff and rollback UX

**Acceptance:**

- review changes before publish;
- reason/reference supported;
- production confirmation names environment;
- publish success shows new revision;
- revision diff/history;
- rollback explains it creates a new revision;
- ambiguous network failure reconciles current server state before retry.

## LF-0606 - SDK key and audit UI

**Acceptance:**

- create/rotate/revoke key;
- secret displayed once;
- key fingerprint/status/last-used safe metadata;
- role restrictions;
- audit filters;
- no secret rendered again from server;
- E2E create/rotate/revoke/denial tests.

---

# Epic E7 - Durable Distribution with Kafka and Redis

## LF-0701 - Transactional outbox publisher

**Acceptance:**

- pending outbox rows leased/processed safely by multiple workers;
- Kafka ack required before publication marked complete;
- transient retry/backoff;
- duplicate publication possible and explicitly supported;
- oldest pending age metric;
- kill/restart integration test proves no lost committed revision.

## LF-0702 - Kafka revision event contract

**Acceptance:**

- versioned event envelope;
- partition key is stable environment ID;
- no secret/context payload;
- producer/consumer schema/contract test;
- duplicate and replay safe;
- topic/config documented for local/prod assumptions.

## LF-0703 - Redis current snapshot materialization

**Acceptance:**

- projector consumes revision event;
- verifies revision/content;
- ignores old/duplicate revisions;
- writes current snapshot/revision/checksum to Redis;
- Redis can be rebuilt from authoritative sources;
- projection metrics.

## LF-0704 - Redis invalidation notification

**Acceptance:**

- bounded channel design;
- edge instances receive revision hint;
- Pub/Sub loss does not lose configuration;
- edges verify/fetch current state;
- no unbounded channel-per-tenant resource model.

## LF-0705 - Multi-edge consistency

**Acceptance:**

- run at least two edge instances locally/test;
- clients connected to different edges converge to same published revision;
- rolling edge restart causes SDK reconnect without evaluation loss;
- revision diagnostic proves convergence;
- no sticky-session requirement.

## LF-0706 - Broker/cache failure drills

**Acceptance:**

- Kafka stopped during publish -> DB/outbox succeeds and later catches up;
- projector stopped -> lag/backlog observed and recovers;
- Redis flush -> snapshots rebuild;
- Redis unavailable -> controlled DB fallback/LKG behavior;
- evidence documented in runbook/reliability report.

---

# Epic E8 - Optional Analytics

## LF-0801 - Evaluation event contract

**Acceptance:**

- analytics explicitly opt-in;
- batchable event schema;
- excludes raw arbitrary context by default;
- supports private/excluded attributes policy;
- event IDs/time/project/environment/flag/variation bounded;
- analytics never required to return evaluation result.

## LF-0802 - Analytics ingestion

**Acceptance:**

- separate endpoint/path/policy;
- client/server key scope appropriate;
- request/batch limits;
- backpressure/drop policy favors config availability;
- invalid events rejected safely;
- metrics for accepted/rejected/dropped.

## LF-0803 - ClickHouse storage

**Acceptance:**

- local optional profile starts ClickHouse;
- schema supports time/flag/variation aggregate queries;
- batched inserts;
- retention policy documented;
- no PII/raw context columns by default;
- integration test.

## LF-0804 - Analytics query API/UI

**Acceptance:**

- bounded aggregate queries;
- time range/flag/variation;
- no claim of causal experiment significance without correct statistics;
- UI labels results operationally;
- query load cannot starve control/config path.

## LF-0805 - Analytics failure isolation

**Acceptance:**

- ClickHouse unavailable does not break snapshot/publish/evaluation;
- bounded queue/drop behavior;
- alerts/metrics;
- recovery documented/tested.

---

# Epic E9 - Security Hardening

## LF-0901 - SDK key hardening

**Acceptance:**

- cryptographically strong secret;
- lookup ID + verifier;
- constant-time comparison;
- one-time display;
- rotate/revoke;
- active streams/snapshot requests observe revocation within documented bound;
- no secret in logs/audit.

## LF-0902 - Distributed rate limits and abuse controls

**Acceptance:**

- independent policies for management, snapshot, SSE, analytics;
- trusted auth partitioning;
- Redis-backed distributed state where needed with safe fallback;
- body/connection limits;
- 429 contract;
- tests.

## LF-0903 - Security headers and CORS

**Acceptance:**

- HSTS production;
- CSP for console;
- nosniff/referrer/frame policy;
- browser SDK CORS explicit;
- server endpoints not wildcarded unnecessarily;
- automated header tests.

## LF-0904 - Audit retention and export

**Acceptance:**

- immutable audit append flow;
- safe filtered export;
- retention policy;
- destructive retention preview/dry run before enablement;
- secrets excluded;
- tenant scope tests.

## LF-0905 - Log privacy and redaction tests

**Acceptance:**

- representative requests with fake secrets/context;
- captured structured logs prove secret/session/context exclusion/redaction;
- high-cardinality metric labels checked;
- tests fail if key material appears.

## LF-0906 - Threat model and remediation review

**Acceptance:**

- threats from `docs/09_SECURITY_PRIVACY.md` assessed;
- evidence/file paths;
- unresolved risks ranked;
- fixes made only as explicit issues;
- security checklist added to release docs.

---

# Epic E10 - Reliability and Performance

## LF-1001 - OpenTelemetry foundation

**Acceptance:**

- management/edge/projector instrumentation;
- W3C trace/correlation;
- safe structured logs;
- OTLP configurable/disableable;
- no secrets/high-cardinality context;
- local collector profile.

## LF-1002 - Operational metrics/dashboards/alerts

**Acceptance:**

- publish/outbox/Kafka/Redis/edge/SSE metrics;
- bounded labels;
- Grafana dashboard;
- example alert rules;
- revision diagnostics;
- screenshots use fictional data.

## LF-1003 - JMH evaluator benchmark suite

**Acceptance:**

- documented hardware/runtime methodology;
- several evaluator scenarios;
- allocation measurements;
- raw result artifact;
- benchmark does not make network call;
- no resume claim automatically generated.

## LF-1004 - HTTP/SSE load suite

**Acceptance:**

- reproducible k6/Gatling scripts;
- snapshot cached/cold scenarios;
- SSE connection/reconnect scenario;
- publish convergence timestamps;
- report includes limitations;
- failures do not corrupt revisions.

## LF-1005 - Durable local last-known-good snapshot

**Acceptance:**

- optional SDK disk LKG;
- atomic write/rename;
- schema/checksum validation;
- older/corrupt file rejected safely;
- startup can activate valid LKG before network;
- filesystem security note;
- tests.

## LF-1006 - Failure runbooks and drills

**Acceptance:**

- all required runbooks completed;
- at least Kafka, Redis, edge restart, bad revision rollback drills executed locally/staging;
- expected vs actual recorded;
- operational gaps create follow-up issues;
- no fabricated availability claims.

---

# Epic E11 - Containers and Kubernetes

## LF-1101 - Production container images

**Acceptance:**

- non-root multi-stage images;
- minimal runtime;
- health/probe support;
- no secrets;
- OCI labels;
- image scan;
- SDKs not containerized.

## LF-1102 - Production-shaped Docker Compose

**Acceptance:**

- profile-driven complete local stack;
- dependency health gates;
- explicit migration step/order;
- fictional seed/demo works;
- shutdown/reset documented;
- optional analytics/observability profiles.

## LF-1103 - Helm deployment

**Acceptance:**

- chart renders management/edge/projector/web;
- external DB/Kafka/Redis assumptions;
- probes/resources/service accounts/ingress/secrets references;
- PDB/HPA where justified;
- no plaintext production secrets;
- `helm lint/template` CI.

## LF-1104 - Local Kubernetes resiliency proof

**Acceptance:**

- deploy to local cluster;
- migrations before workloads;
- edge pod restart;
- rolling replacement;
- SDK reconnect/LKG demonstrated;
- config revision remains correct;
- commands/evidence documented.

---

# Epic E12 - CI/CD and Supply Chain

## LF-1201 - Full PR quality gates

**Acceptance:**

- Java format/static/unit/architecture/integration;
- JS lint/test/build/golden;
- schemas/contracts;
- E2E smoke;
- docs validator;
- Helm/Compose validation;
- protected main expectation documented.

## LF-1202 - Dependency/secret/container scanning

**Acceptance:**

- Maven/npm dependency policy;
- secret scan;
- release image scan;
- high/critical gate policy documented;
- exceptions require explicit documented acceptance;
- actions pinned.

## LF-1203 - SBOM and provenance

**Acceptance:**

- release image SBOM;
- provenance/attestation;
- immutable GHCR or chosen registry digest;
- artifact metadata tied to Git SHA/tag;
- verification documented.

## LF-1204 - Staging promotion

**Acceptance:**

- release builds once;
- same digest deploys staging;
- migration step;
- smoke covers OIDC/publish/edge/SSE/demo;
- failed smoke blocks promotion.

## LF-1205 - Protected production and rollback

**Acceptance:**

- explicit protected approval;
- same staging-tested digest;
- no workflow credentials in source;
- application rollback to compatible digest;
- DB migrations not auto-reversed;
- configuration rollback remains product revision workflow.

---

# Epic E13 - Demo, Portfolio and Commercial Readiness

## LF-1301 - Fictional deterministic demo seed

**Acceptance:**

- fictional organization/projects/environments/flags;
- several targeting/rollout examples;
- deterministic users/contexts;
- reset command;
- no real personal/customer data.

## LF-1302 - Integration guides

**Acceptance:**

- Java quick start;
- Spring Boot quick start;
- JS/React quick start;
- key safety explanation;
- local vs hosted endpoints;
- troubleshooting;
- copy/paste examples tested.

## LF-1303 - Recruiter demo media

**Acceptance:**

- reproducible two-minute script;
- screenshots/video from real running system;
- live flag update;
- deterministic rollout;
- kill switch;
- revision/audit;
- brief architecture/metrics;
- no misleading edited behavior.

## LF-1304 - Case-study README

**Acceptance:**

- problem/architecture/tradeoffs;
- real diagrams;
- exact stack;
- testing/reliability;
- measured benchmarks only;
- limitations/future;
- resume bullets use only verified achievements.

## LF-1305 - Pilot/commercial package

**Acceptance:**

- narrow target customer/developer persona;
- hosted/local pilot checklist;
- provisional pricing hypotheses clearly labeled;
- feedback questions;
- operational boundaries/support expectations;
- no payment system required before user validation.
