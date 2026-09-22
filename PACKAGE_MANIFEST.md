# Package Manifest

This repository contains the LaunchForge implementation and its internal maintenance package. The current
status is M1-M13 complete for the documented local portfolio/demo scope; hosted production-release
evidence remains explicitly tracked in `docs/27_FINAL_ARCHITECTURE_REVIEW.md`.

## Inventory snapshot

| Area | Committed inventory |
|---|---|
| Product and engineering documentation | 28 numbered documents, from `docs/00_DOCUMENT_MAP.md` through `docs/27_FINAL_ARCHITECTURE_REVIEW.md` |
| Architecture decisions | 8 ADRs plus `docs/decisions/README.md` |
| Internal implementation workflow | 16 ordered implementation prompts plus the root handoff and status documents |
| Java workspace | 13 Maven child modules under `backend/`, `sdks/java/`, `demos/`, and `tests/` |
| JavaScript workspace | Admin console, JavaScript core/browser/React SDKs, demo storefront, and browser E2E tests |
| Versioned contracts | Evaluator/size golden vectors and configuration/analytics event schemas with examples |
| Delivery automation | 4 GitHub Actions workflows, production Dockerfiles, local Compose, Helm, and release-manifest tooling |
| Engineering automation | 10 top-level engineering scripts and repository-contract tests under `eng/tests/` |

## Root control and handoff documents

- `README.md` — product overview, measured case study, quick starts, limitations, and repository map;
- `AGENTS.md` — repository implementation and safety rules;
- `.ai-agent/start-here.md` and `.ai-agent/prompt-sequence.md` — ordered implementation handoff;
- `.ai-agent/master-implementation-spec.md` — generated combined specification; never edit directly;
- `PROJECT_STATUS.md`, `IMPLEMENTATION_CHECKLIST.md`, and `CHANGELOG.md` — current progress and
  history;
- `PACKAGE_MANIFEST.md` — this inventory.

## Product, architecture, and review documentation

The 28 numbered documents under `docs/` cover product requirements, architecture, persistence,
APIs, evaluation semantics, SDKs, real-time distribution, frontend UX, security/privacy, testing,
observability, DevOps, performance, milestones, backlog/acceptance, demo, commercialization,
runbooks, technology pins, interview material, non-goals, security/reliability reviews, release
supply chain, integration quick starts, the pilot package, and the final architecture review.

Eight decisions under `docs/decisions/` record the control-plane/data-plane split, local SDK
evaluation, deterministic rollout, transactional outbox, rebuildable Redis materialization, OIDC
BFF sessions, optional ClickHouse analytics, and the Java 25/Spring Boot 4 baseline.

## Implemented application and SDK workspaces

- `backend/launchforge-domain/` — framework-free tenancy, flag, targeting, publication, and SDK-key
  domain types;
- `backend/launchforge-application/` — use cases and ports;
- `backend/launchforge-contracts/` — versioned snapshot, event, and credential contracts;
- `backend/launchforge-infrastructure/` — JDBC persistence, Flyway migrations, and canonical
  snapshot infrastructure;
- `backend/launchforge-control-api/` — OIDC/BFF management API, authorization, publication,
  audit, key lifecycle, analytics queries, and operations endpoints;
- `backend/launchforge-config-edge/` — authenticated snapshot/bootstrap, SSE delivery, Redis-first
  reads, bounded database fallback, and analytics ingestion;
- `backend/launchforge-event-worker/` — outbox publication, signed Redis projection,
  reconciliation, and optional analytics delivery;
- `backend/launchforge-migrator/` — one-shot forward-only Flyway release utility;
- `frontend/admin-web/` — responsive React/TypeScript administration console;
- `sdks/java/launchforge-java-sdk/` — local Java evaluation, refresh/stream fallback, and optional
  last-known-good persistence;
- `sdks/javascript/packages/core/`, `sdks/javascript/packages/browser/`, and
  `sdks/javascript/packages/react/` — shared evaluator plus browser and React delivery integrations;
- `demos/spring-demo/` and `demos/react-storefront/` — executable Java/Spring and browser demo
  paths.

The root `pom.xml` aggregates 13 Maven child modules. The root `package.json` and
`pnpm-workspace.yaml` define the JavaScript packages and browser test workspaces.

## Contracts, tests, and evidence

- `contracts/golden-vectors/` contains the cross-SDK evaluator, JSON-value byte-size, and snapshot
  size boundaries;
- `contracts/events/` contains versioned configuration and evaluation-event schemas/examples;
- `tests/architecture-tests/` enforces Java module boundaries;
- `tests/integration-tests/` exercises PostgreSQL, Kafka, Redis, ClickHouse, TLS, tenancy,
  publication, distribution, and failure behavior with containers;
- `tests/performance/` contains the isolated JMH harness and bounded k6 workloads;
- `tests/e2e/`, Admin Playwright coverage, and demo Playwright coverage exercise browser flows;
- `artifacts/` stores committed, reproducible local review/demo/performance evidence where the
  source documentation identifies it.

## Deployment, release, and security assets

- `compose.yaml` plus `deploy/local/` provide profile-driven local identity, distribution,
  analytics, observability, platform, demo, and transport-security configurations;
- `deploy/docker/` contains digest-pinned multi-stage Java, migrator, web, and demo-capture images;
- `deploy/helm/launchforge/` contains the chart, schema, probes, migration hook, security contexts,
  availability controls, and external-secret references;
- `deploy/release/` contains compatibility and immutable release-manifest inputs;
- `.github/workflows/` contains CI, release, production promotion, and production rollback;
- `.github/rulesets/`, `.github/CODEOWNERS`, and `security/` contain desired repository controls
  and machine-validated supply-chain exception policy.

Hosted environment configuration and same-digest staging/production execution are not bundled or
claimed by this manifest. Their remaining review status is authoritative in
`docs/27_FINAL_ARCHITECTURE_REVIEW.md` and `PROJECT_STATUS.md`.

## Engineering scripts and templates

`eng/` contains documentation/spec synchronization, repository/supply-chain/transport validation,
release-manifest assembly, deterministic demo seed/media automation, local demo orchestration,
materialization key generation, and the kind resilience proof. `templates/` contains eight
environment, application, snapshot, completion, issue/PR, seed, and golden-vector examples.
