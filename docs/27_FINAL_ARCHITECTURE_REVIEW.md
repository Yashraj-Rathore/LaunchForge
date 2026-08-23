# 27 — Final Architecture Review

**Review date:** 2026-08-21

**Prompt:** 15 — final architecture, security, compatibility, failure-mode, test, claim, and toolchain review

**Disposition:** Review complete. P15-01 through P15-03 and P15-05 through P15-10 were corrected in
separately approved follow-ups between 2026-08-21 and 2026-08-23. P15-04 is explicitly deferred,
not resolved, and must be resumed before the final hosted-release review. P15-11 through P15-13
retain their original Low ranking.

## Executive decision

LaunchForge has no Critical finding in the reviewed repository, and the implemented evaluator,
tenant-scoped management paths, immutable publication model, outbox ordering model, SDK
last-known-good behavior, and module boundaries have substantial automated evidence. It is suitable
for its current local portfolio/demo purpose.

It is **not ready for a hosted production pilot or release** until the one unresolved High finding
is corrected and revalidated. The desired `main` ruleset is installed but disabled, while the
created deployment environments still lack real identity/infrastructure and promotion evidence.
P15-01 through P15-03 and P15-05 through P15-10 no longer contribute to the unresolved counts.

| Severity | Count | Meaning in this review |
|---|---:|---|
| Critical | 0 | No demonstrated unauthenticated compromise, cross-tenant API access, secret disclosure, or deterministic-evaluation corruption was found. |
| High | 1 | Unresolved release blocker with a credible change-control consequence. |
| Medium | 0 | All four original Medium findings were corrected and regression-tested. |
| Low | 3 | Documentation or forward-toolchain debt with limited current runtime impact. |

## Resolved since review

#### P15-01 — Redis materialization can become the runtime author without authoritative provenance

**Resolved 2026-08-21.** Event Worker now signs a domain-separated, versioned Ed25519 envelope that
binds environment ID, revision, schema version, checksum, and canonical snapshot; a separate
signature protects lightweight revision polling. Config Edge receives only a bounded set of trusted
public keys, verifies provenance before treating Redis as a hit, and rejects revisions below its
bounded observed watermark. Invalid cache content falls back to PostgreSQL and otherwise fails
closed so SDK last-known-good/default behavior remains intact.

Local Compose and Helm now provide distinct Management, Config Edge, and Event Worker Redis users
and secret-backed passwords. Redis ACLs permit only Event Worker to write runtime snapshot keys, and
Edge no longer backfills them. Worker reconciliation remains the authoritative rebuild path.

Correction evidence:

- `RedisMaterializationProvenanceTest` and `RedisMaterializationVerifierTest` cover field binding,
  untrusted signers, tampering, key rotation, and malformed trust configuration.
- `RedisBackedEdgeRepositoryTest` proves a forged self-consistent value is not served when
  PostgreSQL is unavailable and an older signed value is rejected after a newer observation.
- `DistributionPipelineIT` uses real Redis ACL identities and proves forbidden writes, forged and
  replayed materializations returning the authoritative revision, rebuild, and outage fallback.
- `compose.yaml`, `deploy/local/redis/launchforge-redis-entrypoint.sh`, and the Helm chart render the
  separated credentials and worker-only private signing key.

#### P15-02 — Analytics can block configuration distribution on the shared scheduler

**Resolved 2026-08-23.** Event Worker now owns two named, bounded scheduling resources. A
two-thread configuration scheduler runs outbox publication and PostgreSQL-to-Redis reconciliation,
while a separate single-thread analytics scheduler runs the optional synchronous ClickHouse flush.
The fixed set of periodic jobs, finite analytics queue and insert batch, and bounded ClickHouse
connect/request timeouts keep both resource lanes bounded. Analytics remains conditional and does
not allocate its scheduler when disabled.

Correction evidence:

- `WorkerSchedulingConfigurationTest` proves finite independent pool sizes and verifies that all
  three scheduled jobs select the intended named scheduler.
- `DistributionPipelineIT` sends analytics to an endpoint that accepts the connection but never
  returns an HTTP response, stops Kafka projectors, and proves the real outbox publisher and
  PostgreSQL reconciler both publish/materialize the revision within the five-second configuration
  SLO while the ClickHouse request remains blocked.
- Existing `AnalyticsEventBufferTest` and `AnalyticsWorkerProperties` coverage retains the finite
  queue/batch drop behavior and bounded ClickHouse transport-timeout contract.

#### P15-03 — The Helm production path did not completely model Kafka or Redis transport security

**Resolved 2026-08-23.** Production Helm defaults now require Kafka `SASL_SSL` and Redis TLS while
retaining the three process-specific Redis ACL users. Config Edge and Event Worker receive Kafka
SASL credentials only through Kubernetes Secret key references; all transport trust anchors are
mounted from the runtime Secret and selected through named Spring PEM SSL bundles. The explicit
kind override and base Compose topology remain plaintext local-development exceptions.

An optional Compose overlay provides a reproducible TLS-only Redis and SASL/TLS Kafka topology
without committing certificate or password values. Repository contract validation inspects the
effective secure Helm, local-kind Helm, and merged secure Compose renders so a future change cannot
silently drop TLS, authentication, trust mounts, or secret references.

Correction evidence:

- `TransportSecurityIT` starts real TLS-only Redis and Kafka services, proves the Spring clients
  connect with trusted certificates and valid credentials, and proves bad credentials are denied.
- The three application configurations expose Redis SSL and Kafka security/SASL settings without
  changing local defaults.
- `deploy/helm/launchforge`, `deploy/local/compose.transport-security.yaml`, and
  `eng/validate_transport_security.py` model and enforce the production and local exceptions.

#### P15-05 — The 64 KiB JSON variation limit was measured differently across publication and SDKs

**Resolved 2026-08-23.** Management now validates I-JSON, canonicalizes it, and measures the
canonical representation in UTF-8 bytes before accepting a variation. The domain guard also uses
UTF-8 bytes, so persistence and publication cannot admit a multibyte value that either SDK would
reject. Both SDK parsers retain their canonical UTF-8 enforcement.

Correction evidence:

- `contracts/golden-vectors/json-variation-size-v1.json` defines one language-neutral multibyte
  value exactly at 65,536 bytes and one immediately above it.
- `FlagDefinitionTest` and `JacksonSnapshotCodecTest` prove the domain/management byte boundary and
  that raw whitespace is measured only after canonicalization.
- `ControlPlanePostgresIT` creates and publishes the accepted value through the management API and
  rejects the oversized value.
- Java `GoldenVectorCorpusTest` and TypeScript `golden-corpus.test.ts` execute the same shared
  boundary parameters through their production snapshot parsers.

#### P15-06 — Data-plane configuration permitted snapshots above the normative 5 MiB ceiling

**Resolved 2026-08-23.** The Control API publisher, Config Edge, and Event Worker now use one
backend `SnapshotContract` ceiling of 5,242,880 canonical UTF-8 bytes. Edge and Worker retain their
lower configurable defaults and may be tightened by operators, but configuration above the
version-1 ceiling fails validation instead of allowing materialization that either SDK would later
reject. The standalone SDKs remain server-independent and verify their constants against the same
language-neutral contract.

Correction evidence:

- `SnapshotContractTest`, `ConfigEdgePropertiesTest`, and `DistributionPropertiesTest` prove the
  shared backend maximum accepts the exact ceiling and rejects the next byte.
- `ControlPlaneService` uses the same backend constant for publication.
- `contracts/golden-vectors/snapshot-size-v1.json` defines exact 5 MiB and one-byte-over boundaries.
- Java `GoldenVectorCorpusTest` and TypeScript `golden-corpus.test.ts` generate a valid exact-limit
  snapshot, activate it through their production parsers, and reject the next byte.

#### P15-07 — Browser analytics transport had no bounded request timeout

**Resolved 2026-08-23.** Browser analytics batches now own an `AbortController` and a configurable
request timeout that defaults to two seconds and is bounded from 100 milliseconds through 30
seconds. A timeout aborts the fetch, accounts for the failed/dropped optional batch, clears the
in-flight promise, and permits the next batch to flush. Client close also aborts any in-flight
analytics request without changing local evaluation behavior.

Correction evidence:

- `LaunchForgeBrowserClient` applies the cancellation signal and clears timeout/controller state
  in `finally`.
- `client.test.ts` uses a never-resolving fetch, advances a fake clock to the timeout, verifies the
  abort/failure accounting, and proves a later batch succeeds.
- The pinned Node 24 validation target passed formatting, lint, typecheck, all tests, and builds.

#### P15-08 — Tenant-owned audit and SDK-key lineage chains lacked compound foreign keys

**Resolved 2026-08-23.** Flyway V7 enforces nullable audit project ownership through
`(organization_id, project_id)` and audit environment ownership through
`(organization_id, project_id, environment_id)`. It also replaces global-ID-only SDK-key rotation
lineage with a same-organization/project/environment compound relationship. The supported release
schema range advances additively from V6 to V6-V7.

Correction evidence:

- `V7__tenant_relationship_integrity.sql` adds the ownership constraints and required scoped SDK
  key uniqueness.
- `ControlPlanePostgresIT` proves raw cross-organization audit references, cross-project audit
  environments, and cross-environment SDK-key predecessor references are rejected by PostgreSQL.

#### P15-09 — The README overstated organization onboarding

**Resolved 2026-08-23.** The public capability statement now says operators work within a
provisioned organization and can create projects/environments. The README explicitly states that
self-service organization creation, rename, suspension, closure, and billing lifecycle are not
implemented. This selects the approved truthful-claim correction without inventing a new product
surface.

Correction evidence: the README capability and current-limits sections now match the implemented
organization controller and the MVP product/API scope.

#### P15-10 — One invalid authoritative snapshot could starve reconciliation after its cursor

**Resolved 2026-08-23.** Reconciliation now catches validation/materialization failure per
environment, increments the existing error signal, emits a bounded safe environment/revision log,
and continues through later rows and pages. The cursor advances, but the poison row is neither
accepted nor permanently skipped: the next complete scan retries it from the beginning.

Correction evidence:

- `ProjectionReconcilerTest` proves a poison row does not block a healthy later page, the cursor
  advances, and error/advanced metrics are both recorded.
- `DistributionPipelineIT` inserts an invalid lower-ordered current snapshot and proves the real
  PostgreSQL-to-Redis reconciler still restores a healthy environment's signed current revision.

## Ranked unresolved findings

### High

#### P15-04 — Required GitHub change and deployment controls are not fully active

The repository documents branch protection and `staging`/`production` GitHub Environments as owner
setup. The initial review queried the live repository on 2026-08-21: repository rulesets were empty,
the `main` branch-protection endpoint reported that the branch was not protected, and the
environments collection was empty.

Partial hardening on 2026-08-23 activated a no-bypass `v*` tag ruleset that blocks update and
deletion, created tag-restricted `staging` and `production` environments, and made production
fail-closed with a required reviewer and self-approval disabled. The exact desired active ruleset
payloads are now versioned and contract-tested under `.github/rulesets/`. The complete `main`
ruleset is installed live but remains disabled because the repository has only one collaborator;
activating mandatory non-self review without another trusted reviewer would make `main`
unmaintainable. The environments do not yet have cluster/OIDC configuration, required variables,
or secrets, and no tagged staging/promotion run exists. Direct pushes to `main` therefore remain
possible and the production workflow cannot yet prove its intended approval and same-digest
boundary.

**Deferred by repository owner on 2026-08-23.** This finding remains High and must be resumed during
the final hosted-release review; deferral does not authorize a production release or pilot.

This is accurately disclosed in `PROJECT_STATUS.md`; the finding is an operational release blocker,
not a misleading code claim.

Evidence:

- `.github/rulesets/main.json`, `.github/rulesets/release-tags.json`, `.github/CODEOWNERS`,
  `.github/workflows/ci.yml`, `.github/workflows/release.yml`, and
  `.github/workflows/promote-production.yml`
- `docs/24_RELEASE_SUPPLY_CHAIN.md`
- live GitHub API responses for `Yashraj-Rathore/LaunchForge` on 2026-08-21 and 2026-08-23

Remaining correction evidence: add a second trusted collaborator, activate the installed `main`
ruleset with the six documented required checks plus review/CODEOWNERS/history protections,
configure the environments with real provider OIDC or the documented narrowly scoped secrets and
variables, and complete one approved same-digest staged promotion.

### Low

#### P15-11 — Event Worker database defaults are unsafe for a production artifact

The Event Worker `application.yml` defaults the database username/password to
`launchforge`/`launchforge-local`, while the other database deployables fail closed and Helm
overrides the values from a Secret. This is mainly an unsafe standalone-misconfiguration path and a
violation of the no-hard-coded-credentials repository rule.

#### P15-12 — `PACKAGE_MANIFEST.md` describes only the original M0 shell

The manifest says the package contains documents 00–21 and defers product behavior, while the
repository now contains completed M1–M13 implementations and documents through this review. It is
stale packaging documentation. It was intentionally not corrected during this review.

#### P15-13 — Forward-JDK test instrumentation needs an explicit Mockito agent

The successful Java build warns that Mockito is self-attaching the Byte Buddy agent and that dynamic
agent loading will be disabled by default in a future JDK. This does not fail Java 25.0.4 today but
is predictable toolchain debt. The separately documented Temurin runtime-image patch exception
(25.0.3 image versus 25.0.4 CI/host) also remains open and accurately disclosed in
`docs/19_TECHNOLOGY_BASELINE.md`.

## Security and tenant-isolation review

P15-11 is the only unresolved security-adjacent finding; P15-01, P15-03, and P15-08 are resolved as
recorded above. No cross-organization API access was reproduced. Server-derived organization
scope, role checks, database-enforced compound ownership on tenant relationship chains, SDK
credential-class separation, hash-only server-key verification, CSRF/OIDC/session
boundaries, and privacy-safe request logging were traced in code and exercised by the 28-test
container integration suite. Direct-resource cross-tenant access, Viewer denial, final-Owner
concurrency, revoked key denial, CORS separation, and tenant-scoped analytics are covered.

No Critical secret exposure was found in tracked files or rendered templates. Local fictional
credentials remain environment-supplied. Feature values continue to be correctly documented as
non-secret data.

## Java/TypeScript evaluator drift review

No semantic evaluator drift was found. Both implementations consume
`contracts/golden-vectors/evaluator-v1.json`; Java `GoldenVectorCorpusTest` and TypeScript
`golden-corpus.test.ts` cover identical operators, types, reason codes, Unicode/missing/null/SemVer
semantics, exact SHA-256 rollout boundaries, malformed snapshots, and the deterministic 10,000
subject sample. The Java reactor and pinned-Node frontend validation both passed that corpus during
this review.

P15-05 and P15-06 were pre-evaluation snapshot acceptance/size compatibility gaps, not differences
in rule evaluation meaning, and are resolved as recorded above.

## Revision, event, and snapshot compatibility review

No ordering or schema-version defect was found in the normal publication pipeline. PostgreSQL
publication atomically creates an immutable higher revision, audit row, environment pointer, and
outbox row. Event consumers use environment ID as the key, revision as ordering authority, reject
unsupported schemas, and are idempotent for duplicate/stale delivery. Rollback creates a newer
revision. The full distribution integration test passed PostgreSQL → outbox → Kafka → Redis → Edge
→ SDK convergence and stale/duplicate/rebuild behavior.

No snapshot or evaluator compatibility finding remains from this review. The former P15-01
provenance, P15-02 scheduler-isolation, P15-05 UTF-8 value-sizing, and P15-06 snapshot-ceiling
defects are resolved as recorded above.

## Failure-mode gaps

No unresolved Medium failure-mode gap remains. P15-07 adds a bounded abortable browser analytics
request, and P15-10 isolates poison reconciliation rows while allowing healthy environments to
recover. Existing evidence also covers SDK last-known-good/default behavior, stream-to-poll
fallback, Redis loss/rebuild, duplicate/stale events, key revocation, atomic publication failure,
edge restart, rollback, and disabled analytics.

## Test and operational-evidence gaps

Each P15 finding needs the focused regression evidence stated with it. In addition:

- the final checklist's clean-clone four-minute demo run has not been executed after this review;
- controlled load evidence is local and bounded, not production capacity proof;
- the release/promotion/restore workflows have not run against configured hosted environments;
- the remaining P15-11 through P15-13 items need their focused regression/documentation evidence.

## README, resume, demo, and commercial-claim review

P15-09 is resolved by narrowing the organization claim to the implemented provisioned-organization
workflow and explicitly stating the absent self-service lifecycle. P15-12 remains stale package
metadata. The case-study throughput and propagation numbers are correctly labeled as local
controlled measurements, the demo/customer names are fictional, pilot pricing is explicitly a
hypothesis, and no customers, revenue, production capacity, multi-region deployment, or completed
hosted release are claimed. The four-minute demo pauses are presentation pacing rather than
artificial configuration latency.

## Dependency and toolchain review

No current dependency-convergence, formatting, Checkstyle, TypeScript, lockfile, supply-chain
policy, or known-vulnerability gate failure was observed. Maven Enforcer and the frontend frozen
lockfile passed; the repository supply-chain validator passed; GitHub CI remains the time-sensitive
source for dependency review, Trivy filesystem/image scans, action pinning, and OIDC/release policy.

P15-13 records the forward-JDK Mockito warning and known runtime image patch exception. Shading the
standalone JMH harness also emits expected module/duplicate metadata warnings; no runtime failure was
demonstrated, but a future packaging task should explicitly verify the benchmark artifact after
dependency changes.

## Staged correction issue list

Prompt 15 recorded these items without implementing them. The list below now records corrections
approved and completed after that review alongside the remaining work.

### Stage 0 — hosted-release blockers

1. **P15-01 (resolved 2026-08-21):** authenticated Redis materialization provenance and
   least-privilege ACLs established and regression-tested.
2. **P15-02 (resolved 2026-08-23):** analytics and configuration schedulers are isolated and the
   ClickHouse non-response drill proves distribution progress.
3. **P15-03 (resolved 2026-08-23):** Kafka/Redis TLS, secret-backed Kafka SASL, rendered-contract
   validation, and authenticated TLS integration evidence are established.
4. **P15-04 (deferred 2026-08-23; unresolved):** during the final hosted-release review, add an
   independent reviewer, activate the installed `main` ruleset, configure environment identity,
   and execute the first staged promotion.

### Stage 1 — compatibility and tenant integrity

5. **P15-05 (resolved 2026-08-23):** canonical UTF-8 management enforcement and shared management,
   Java SDK, and TypeScript SDK multibyte boundaries are established.
6. **P15-06 (resolved 2026-08-23):** the backend shares and caps the normative 5 MiB snapshot
   ceiling, and both SDK parsers execute the same exact boundary contract.
7. **P15-07 (resolved 2026-08-23):** bounded abortable browser analytics requests and
   hanging-fetch/later-batch coverage are established.
8. **P15-08 (resolved 2026-08-23):** V7 compound tenant/lineage constraints and direct database
   rejection tests are established.
9. **P15-11:** remove production-artifact database credential defaults.

### Stage 2 — resilience, product truth, and documentation

10. **P15-10 (resolved 2026-08-23):** poison reconciliation rows are isolated, retried on later
    scans, and cannot starve healthy pages.
11. **P15-09 (resolved 2026-08-23):** the public capability claim now accurately describes
    provisioned organizations and the absent self-service lifecycle.
12. **P15-12:** regenerate the package manifest from the completed repository inventory.
13. **P15-13:** configure explicit Mockito instrumentation and close the runtime image patch exception.

### Stage 3 — final release evidence

14. Run a clean-clone deterministic demo and integration quick starts.
15. Re-run bounded performance/failure drills after Stage 0–2 corrections.
16. Execute a tagged GHCR release, staging smoke, approved same-digest production promotion, and
    restore drill in configured hosted environments.

## Validation performed

- `./mvnw.cmd --batch-mode --no-transfer-progress verify` — passed all 14 reactor modules and 130
  unit/contract/architecture/demo tests, including 8 ArchUnit rules.
- `./mvnw.cmd --batch-mode --no-transfer-progress -pl tests/integration-tests -am verify -Pintegration`
  — passed 25 Testcontainers integration tests with PostgreSQL, Redis, Kafka, and ClickHouse.
- `docker build --target validation --file deploy/docker/Dockerfile.web --tag launchforge-web-prompt15-validation .`
  — passed the pinned Node 24.19.0 / pnpm 11.21.0 format, lint, typecheck, test, and build stages.
- `python eng/validate_docs.py`, `python eng/validate_supply_chain.py`, engineering unit tests,
  deterministic seed check, generated-spec sync check, JSON parsing, Compose render, and Git diff
  checks — passed after this document was added.

The live GitHub Actions results for the published commit are separate evidence and are recorded in
the completion report. A green build does not resolve the documented architectural findings.

### P15-01 correction validation - 2026-08-21

- `./mvnw.cmd --batch-mode --no-transfer-progress -pl backend/launchforge-contracts,backend/launchforge-config-edge,backend/launchforge-event-worker,tests/integration-tests -am test -DskipITs`
  — passed the affected contract, Config Edge, Event Worker, and supporting reactor unit tests.
- `./mvnw.cmd --batch-mode --no-transfer-progress -pl tests/integration-tests -am verify -Pintegration "-Dit.test=DistributionPipelineIT" "-Dfailsafe.failIfNoSpecifiedTests=false"`
  — passed the Docker-backed signed materialization, forged snapshot, signed replay, process ACL,
  reconciliation, multi-edge, and fallback drill with one test and zero failures/errors.
- Compose rendering, strict Helm lint/default and Kind rendering, PowerShell parsing, and generated
  specification synchronization passed with the new credentials and signing configuration.

The full repository validation and live GitHub Actions result for the correction are separate
evidence recorded in its completion report.

### P15-02 correction validation - 2026-08-23

- `./mvnw.cmd --batch-mode --no-transfer-progress verify`
  — passed all 14 reactor modules and 140 unit, contract, architecture, and demo tests, including
  the new scheduler topology coverage.
- `./mvnw.cmd --batch-mode --no-transfer-progress -pl tests/integration-tests -am test-compile -DskipTests -DskipITs`
  — passed affected Java formatting, static analysis, compilation, and test compilation across ten
  supporting reactor modules.
- `./mvnw.cmd --batch-mode --no-transfer-progress -pl tests/integration-tests -am verify -Pintegration "-Dit.test=DistributionPipelineIT" "-Dfailsafe.failIfNoSpecifiedTests=false"`
  — passed the Docker-backed PostgreSQL/Kafka/Redis drill with one test and zero failures/errors,
  including the non-responsive ClickHouse phase, isolated scheduled outbox publication and
  reconciliation, and all prior durability/provenance scenarios.

The full repository validation and live GitHub Actions result for the correction are separate
evidence recorded in its completion report.

### P15-03 correction validation - 2026-08-23

- `./mvnw.cmd --batch-mode --no-transfer-progress verify`
  — passed all 14 reactor modules and 140 unit, contract, architecture, and demo tests.
- `./mvnw.cmd --batch-mode --no-transfer-progress -pl tests/integration-tests -am verify -Pintegration`
  — passed all 26 Docker-backed PostgreSQL, Kafka, Redis, and ClickHouse tests with zero
  failures/errors, including the TLS-only Redis and Kafka SASL/TLS boundary proof.
- Strict Helm lint plus production and kind rendering passed; `eng/validate_transport_security.py`
  accepted the production TLS/secret contract, the explicit local plaintext exception, and the
  merged secure Compose contract.
- The 11 engineering unit tests, documentation and supply-chain validators, generated-spec sync,
  JSON parsing, base/secure Compose validation, and digest-pinned `actionlint` all passed.

The live GitHub Actions result is separate evidence and is not claimed until these changes are
published at the user's request.

### P15-07 through P15-10 correction validation - 2026-08-23

- `./mvnw.cmd --batch-mode --no-transfer-progress verify` — passed all 14 reactor modules,
  including the new poison-row unit regression, formatting, Checkstyle, architecture rules, and
  package builds.
- `./mvnw.cmd --batch-mode --no-transfer-progress -pl tests/integration-tests -am verify -Pintegration`
  — passed all 28 Docker-backed PostgreSQL, Kafka, Redis, ClickHouse, and TLS tests with zero
  failures/errors. The 14-test control-plane class includes direct V7 tenant/lineage rejection;
  the distribution drill includes healthy signed recovery after an earlier poison row.
- `docker build --target validation --file deploy/docker/Dockerfile.web --tag launchforge-web-p15-07-10-validation .`
  — passed pinned Node 24.19.0/pnpm 11.21.0 formatting, lint, typecheck, all frontend/SDK tests, and
  builds; the browser SDK's six tests include the hanging analytics request regression.
- Documentation and supply-chain validators, all 14 engineering unit tests, generated master-spec
  synchronization, release compatibility parsing, and Git diff checks passed.

The live GitHub Actions result is separate evidence and is not claimed until these changes are
published at the user's request.
