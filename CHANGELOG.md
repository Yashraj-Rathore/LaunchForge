# Changelog

## Unreleased

### Documentation

- Added complete LaunchForge architecture, product, SDK, evaluation, security, eventing, testing, operations, deployment, commercialization, and Codex implementation package.
- Normalized the canonical repository/module layout around `launchforge-control-api`, the bounded Java SDK, and the JavaScript core/browser/React workspace.
- Established one normative snapshot representation, RFC 8785 checksum procedure, identifier policy, browser-projection checksum behavior, and algorithm-version-1 evaluator/reason-code contract.
- Defined cross-language numeric, Unicode, missing/null, SemVer, and rollout-input behavior and aligned SDK examples, test vectors, templates, and ADR guidance.
- Recorded Prompt 0 toolchain pins and clarified deferred service, optional analytics, security-hardening, and durable LKG milestone boundaries.
- Finalized the M1 organization/member API, PostgreSQL tenancy/session baseline, OIDC BFF settings, role matrix enforcement, local identity workflow, and Keycloak 26.7.0/container digest pin.
- Finalized the M2 control-plane API, normative rule/rollout projection details, strict I-JSON/RFC 8785 publication contract, PostgreSQL ownership constraints, and the pre-Kafka durable outbox boundary.
- Finalized the M3 Java SDK API, concrete bootstrap/timeout/polling defaults, generated evaluator corpus lifecycle, and the explicit boundary that Config Edge endpoints and SDK-key persistence remain M4 work.
- Finalized the M4 server SDK-key lifecycle, authoritative snapshot headers, revision-only SSE contract, Java SDK stream/fallback behavior, PostgreSQL-first delivery boundary, and reproducible fictional live-update demo.
- Finalized the M5 public browser-key and exact-origin CORS contract, separate client-visible projection checksum/ETag behavior, JavaScript numeric compatibility, React context/subscription lifecycle, and Northstar storefront workflow.
- Finalized the M6 draft-backed simulator, safe tenant-scoped audit query, typed variation-edit contract, production publish review, stale-write reconciliation, and one-time credential display behavior.
- Finalized the M7 leased outbox, environment-keyed revision event, monotonic Redis materialization, global bounded invalidation, Redis-first edge fallback, and failure-recovery operating contracts.
- Finalized the M8 privacy-minimized evaluation event, separate server/browser ingestion paths, ClickHouse retention and aggregation contract, bounded operational query semantics, and analytics failure-isolation runbook.

### Implementation

- Added the Java 25 / Spring Boot 4.1 Maven reactor, committed Maven 3.9.16 wrapper, bounded backend and SDK modules, strict compiler/format/static gates, and executable ArchUnit dependency rules.
- Added the minimal Control API application shell plus a real PostgreSQL Testcontainers connection test, and a digest-pinned PostgreSQL 18.4 local Compose service with health, environment, and lifecycle commands.
- Added the pnpm 11.21 workspace, minimal React 19.2 / strict TypeScript / Vite admin shell, reserved JavaScript SDK package boundaries, and format/lint/typecheck/test/build commands.
- Added SHA-pinned GitHub Actions jobs for backend, PostgreSQL integration, frontend quality, Compose, documentation, and JSON-template validation.
- Corrected the GitHub Actions Temurin selector to the setup-java catalog form for the pinned Java 25.0.4+7 release.
- Moved fictional Keycloak credentials to runtime-resolved realm-import placeholders so fresh CI environments do not require post-start bootstrap-administrator operations.
- Committed the non-secret Spring local profile so CI uses the intended HTTP-only development cookie and opt-in fictional SQL seed settings.
- Implemented LF-0101–LF-0105 with a framework-free organization/membership domain, final-Owner and role invariants, server-derived tenant application ports, scoped JDBC persistence, Flyway V1 schema, transactional membership changes, and safe success/denial audit records.
- Added Spring Security Authorization Code/OIDC with PKCE, state/nonce validation, PostgreSQL-backed sessions, bounded idle/absolute lifetime, secure non-local cookie defaults, local HTTP cookie isolation, CSRF protection, session rotation, and local logout invalidation.
- Added digest-pinned optional Keycloak Compose profiles, runtime-only fictional demo passwords, fictional tenant/project seeds, the authenticated React organization shell, PostgreSQL security integration tests, and a real Keycloak/Chromium login/access/logout CI smoke.
- Implemented LF-0201–LF-0207 with project/environment lifecycle, typed flags and two-to-ten variations, environment drafts, ordered typed rules, integer rollout allocations, deliberate salt reseeding, ETag/If-Match management APIs, and server-derived tenant authorization.
- Added Flyway V2 compound tenant constraints, immutable revision triggers, canonical snapshot/checksum storage, atomic revision/pointer/audit/outbox publication, safe revision diff/history, and rollback as a newer revision with source metadata.
- Added domain, canonicalization, API, and PostgreSQL Testcontainers coverage for operator/type boundaries, I-JSON, stale concurrent writes/publishes, cross-tenant direct IDs, forced transaction rollback, production authorization/reasons, revision immutability, and historical rollback.
- Implemented LF-0301–LF-0307 with a pure Java algorithm-version-1 evaluator, strict immutable snapshot compilation/checksum validation, exact SemVer and SHA-256 rollout semantics, typed value/detail APIs, atomic snapshot activation, and all bounded reason codes.
- Added non-blocking and bounded blocking SDK bootstrap, `LF-SDK` authentication, ETag conditional polling with bounded jitter, coalesced refreshes, in-memory last-known-good retention, idempotent shutdown, and no network/JSON work on the evaluation hot path.
- Generated and froze the language-neutral evaluator-v1 corpus from executable reference code, including every operator/type/reason identifier, Unicode and empty strings, exact rollout boundaries, malformed snapshots, and a deterministic 10,000-subject distribution; exact regeneration is enforced in tests.
- Added concurrent evaluator/activation tests, snapshot outage and invalid-newer-revision tests, safe-integer compatibility fixes in management publication, and a separate fictional Spring storefront that continues two typed evaluations after its snapshot source stops.
- Implemented LF-0401–LF-0406 with an independent Spring Boot WebFlux Config Edge, environment-scoped one-time server SDK keys with hash-only PostgreSQL storage, strict `LF-SDK` authentication, bounded canonical snapshot delivery, ETag/304 support, and revision-only SSE with heartbeats, lifecycle revalidation, connection limits, and active metrics.
- Extended the Java SDK with optional SSE consumption, authoritative conditional refresh after newer hints, exponential reconnect jitter, retained polling fallback, interruption/restart convergence, and clean shutdown while preserving in-memory last-known-good evaluation.
- Added Flyway V3, authenticated key create/list/rotate/revoke APIs, key/edge security and HTTP contract tests, full PostgreSQL/WebFlux/SDK integration coverage for kill-switch publication and revocation, and a live-update Spring demo exposing its active revision.
- Implemented LF-0501–LF-0505 with a strict TypeScript algorithm-version-1 snapshot compiler/evaluator, SHA-256/BigInt rollout hashing, all typed value/detail APIs, and direct execution of the frozen Java golden corpus.
- Added the browser SDK with bounded bootstrap, conditional jittered polling, streaming-fetch SSE, exponential reconnect, immutable snapshot activation, local context replacement, in-memory last-known-good retention, and idempotent cleanup.
- Added Flyway V4, separate public browser client-key management, exact non-credentialed CORS enforcement, server/client key route separation, browser-only projection filtering before checksum/ETag generation, and Java/PostgreSQL/WebFlux security coverage.
- Added the thin React provider and typed hooks plus a fictional Northstar Commerce storefront and Playwright proof for deterministic targeting, deterministic rollout, live revision activation, and kill-switch rendering without redeploy.
- Implemented LF-0601–LF-0606 with an authenticated responsive React console for project/environment navigation, typed flag and variation editing, ordered rules, exact rollout allocation, Java-backed draft simulation, publish review, immutable revision history/diff/rollback, SDK key lifecycle, and filtered audit history.
- Added React Router, TanStack Query, and Zod with exact pins; accessible loading/empty/denied/error states; unmistakable production context; optimistic-conflict messaging that preserves local edits; and Chromium coverage for the production flag journey, key create/rotate/revoke, one-time secrets, and Viewer denial.
- Added a tenant-authorized audit read API, stable-ID variation updates in the existing optimistic transaction, and a narrowly isolated Control API simulator that reuses the pure Java SDK evaluator without persisting or logging evaluation context.
- Implemented LF-0701–LF-0706 with multi-worker expiring outbox leases, broker-acknowledged publication, bounded retry/permanent-failure handling, a versioned additive Kafka contract keyed by environment, and an idempotent PostgreSQL-validating projector.
- Added atomic monotonic Redis snapshot hashes, a PostgreSQL reconciliation rebuild, one global bounded Pub/Sub hint channel, Redis-first Config Edge reads, monotonic cache backfill, and semaphore-bounded PostgreSQL fallback with cache/projection/outbox metrics.
- Added digest-pinned Kafka 4.3.1 and Redis 8.2.8 distribution services plus a Testcontainers drill proving lease recovery, duplicate safety, Kafka catch-up, projector recovery, Redis rebuild/outage fallback, two-edge convergence/restart, and Java SDK last-known-good evaluation.
- Implemented LF-0801–LF-0805 with explicit opt-in Java/browser SDK analytics, context-free bounded batches, tenant scope derived from server/browser keys, a dedicated Kafka topic and bounded Event Worker buffer, and best-effort failure behavior that never changes local evaluation results.
- Added digest-pinned ClickHouse 26.7.1.1315, a 90-day privacy-bounded MergeTree schema, duplicate-tolerant aggregate queries, a tenant-authorized operational analytics UI, Micrometer ingestion/worker/query signals, and real ClickHouse integration coverage for storage, retention, privacy, and duplicate handling.
