# AGENTS.md — Repository Instructions for Codex

## Mission

Build and maintain LaunchForge according to the specifications in this repository. LaunchForge is a multi-tenant feature flag and remote configuration platform with local SDK evaluation, deterministic targeting, real-time configuration delivery, auditable change management, and optional evaluation analytics.

## Required reading order

Before changing code, read:

1. `README.md`
2. `docs/01_PRODUCT_REQUIREMENTS.md`
3. `docs/02_SYSTEM_ARCHITECTURE.md`
4. `docs/05_FLAG_EVALUATION_ENGINE.md`
5. the document(s) linked to the assigned issue
6. `docs/15_BACKLOG_AND_ACCEPTANCE.md`
7. `templates/definition-of-done.md`

`CODEX_MASTER_IMPLEMENTATION_SPEC.md` is generated. Never edit it directly.

If requirements conflict, stop and report the conflict. Do not silently invent a new product direction.

## Architecture rules

- The control plane begins as a **modular monolith**.
- Final deployment may use separate Control API, Config Edge, and Event Worker processes, but business modules are not split into arbitrary microservices.
- Primary backend language: Java.
- Framework: Spring Boot.
- Frontend: React + TypeScript.
- System of record: PostgreSQL.
- Runtime published configuration is immutable and versioned.
- SDK runtime evaluation is local whenever possible.
- Kafka is introduced only when durable multi-node configuration distribution requires it.
- Redis is a rebuildable data-plane materialization/cache and fan-out helper, never the source of truth.
- ClickHouse is analytics-only and deferred until the analytics milestone.
- Browser authentication uses OIDC through a same-origin BFF/session design; never store long-lived access tokens in localStorage.
- Human credentials, Server SDK keys, Browser SDK keys, and future management API credentials are separate credential classes.
- Docker Compose must work before Helm/Kubernetes work begins.

## Java coding rules

- Use the verified Java LTS baseline from `docs/19_TECHNOLOGY_BASELINE.md`.
- No preview, incubator, or experimental Java APIs in production code.
- Domain must not depend on Spring, JPA, HTTP, Kafka, Redis, ClickHouse, or UI packages.
- Prefer explicit, readable domain types over framework-driven behavior.
- Keep controllers thin; business behavior belongs in application/domain services.
- Validate all external input.
- Use optimistic concurrency on management resources.
- Use explicit network/database timeouts where applicable.
- Never swallow exceptions or retry permanent failures blindly.
- Never log secrets, SDK keys, raw cookies, authorization headers, or arbitrary evaluation context.
- No hard-coded credentials, organization IDs, project IDs, URLs, pricing, or demo accounts in production code.
- Do not add a dependency solely because it is popular; document why it is required.

## Evaluation engine rules

- Evaluation must be deterministic for the same snapshot/context.
- Rule semantics are versioned.
- No implicit type coercion.
- Missing attributes have explicit behavior.
- Percentage rollout follows `docs/05_FLAG_EVALUATION_ENGINE.md`.
- Java and TypeScript implementations must pass the same golden test vectors.
- Evaluation returns a safe reason code for diagnostics.
- SDKs use last-known-good or caller defaults if remote configuration is unavailable.
- Local evaluation performs no network, database, or disk I/O.

## Distribution rules

- A management edit does not affect runtime until a validated immutable environment revision is published.
- PostgreSQL commit and Kafka publication use the documented transactional outbox boundary.
- Kafka messages and snapshots are versioned contracts.
- Consumers are idempotent and tolerate duplicate delivery.
- Revision numbers are the ordering authority.
- Redis loss cannot erase authoritative configuration.
- Stream disconnects fall back to bounded polling with exponential backoff and jitter.
- Customer applications continue evaluating with last-known-good/defaults when LaunchForge is unavailable.

## Testing rules

For every behavior change:

1. add/update unit tests;
2. add integration tests for persistence/auth/Kafka/Redis/ClickHouse/HTTP boundaries;
3. update cross-SDK golden vectors if evaluation semantics change;
4. run applicable format/static/build/tests;
5. report exact commands and outcomes.

Critical flows require end-to-end evidence:

- admin creates flag -> publishes -> Java SDK refreshes -> local evaluation returns expected value;
- stable 10% rollout remains stable for same subject and approximately distributes over a deterministic sample;
- kill switch publishes -> connected SDK observes new revision without app redeploy;
- rollback creates a newer revision that restores prior behavior;
- cross-organization direct-ID access is denied;
- duplicate/stale configuration events are harmless;
- edge/Redis restart does not change already published behavior;
- stream disconnect falls back safely;
- revoked SDK key is denied;
- analytics disabled means no evaluation event leaves the SDK.

## Security rules

- HTTPS outside local development.
- Secure, HttpOnly, SameSite session cookies in non-local environments.
- CSRF protection on browser mutations.
- Validate OIDC issuer/signature/state/nonce and audience where required.
- Server SDK keys use strong entropy and hash-only storage where verification semantics permit.
- Browser SDK keys are treated as public and can only retrieve client-visible read-only snapshots.
- Every management operation is organization-scoped and role-authorized.
- Rate-limit login/management/bootstrap/stream/analytics independently.
- Feature flags are not a secrets manager.
- Analytics excludes arbitrary targeting context by default.
- Logs/metrics must not leak secrets or unbounded labels.

## Task execution protocol

For every assigned issue:

1. Restate issue IDs and affected components.
2. Read linked requirements and ADRs.
3. State assumptions/conflicts.
4. Give a small implementation plan.
5. Implement only assigned issue(s).
6. Add tests.
7. Run validation.
8. Update source docs, `PROJECT_STATUS.md`, and `CHANGELOG.md` if behavior changes.
9. Run `python eng/sync_master_spec.py` when source documentation changes.
10. Report files changed, commands/results, remaining risks, and next recommended issue without implementing it.

## Definition of done

An issue is complete only when it satisfies both `docs/15_BACKLOG_AND_ACCEPTANCE.md` and `templates/definition-of-done.md`.
