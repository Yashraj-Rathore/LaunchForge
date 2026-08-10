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
