# 27 — Final Architecture Review

**Review date:** 2026-08-21

**Prompt:** 15 — final architecture, security, compatibility, failure-mode, test, claim, and toolchain review

**Disposition:** Review complete. P15-01 was corrected in a separately approved follow-up on
2026-08-21; the remaining findings retain their original review ranking.

## Executive decision

LaunchForge has no Critical finding in the reviewed repository, and the implemented evaluator,
tenant-scoped management paths, immutable publication model, outbox ordering model, SDK
last-known-good behavior, and module boundaries have substantial automated evidence. It is suitable
for its current local portfolio/demo purpose.

It is **not ready for a hosted production pilot or release** until the three unresolved High
findings are corrected and revalidated. Optional analytics can still block the same default
scheduled-execution lane used by configuration distribution. The Helm chart still lacks a complete
production Kafka/Redis TLS and Kafka authentication model, and the repository currently has no
active GitHub branch rules or deployment environments. P15-01 no longer contributes to that count.

| Severity | Count | Meaning in this review |
|---|---:|---|
| Critical | 0 | No demonstrated unauthenticated compromise, cross-tenant API access, secret disclosure, or deterministic-evaluation corruption was found. |
| High | 3 | Unresolved release blocker with a credible availability, transport-security, or change-control consequence. |
| Medium | 6 | Contract, tenant-integrity, resilience, or product-completeness gap that must be scheduled before broad use. |
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

## Ranked unresolved findings

### High

#### P15-02 — Analytics can block configuration distribution on the shared scheduler

`AnalyticsEventBuffer.flush`, `OutboxPublisher`, and `ProjectionReconciler` are all `@Scheduled` in
the Event Worker. No dedicated `TaskScheduler`, task-specific executor, or scheduling pool is
configured. The analytics flush performs synchronous `HttpClient.send` to ClickHouse and can occupy
the default scheduling lane for its configured request timeout. With analytics enabled, a slow or
hung ClickHouse request can therefore delay outbox publication and Redis reconciliation, contrary
to the requirement that analytics never affect configuration delivery.

Evidence:

- `backend/launchforge-event-worker/src/main/java/dev/launchforge/eventworker/analytics/AnalyticsEventBuffer.java` — scheduled blocking flush
- `backend/launchforge-event-worker/src/main/java/dev/launchforge/eventworker/analytics/ClickHouseAnalyticsStore.java` — synchronous HTTP insert
- `backend/launchforge-event-worker/src/main/java/dev/launchforge/eventworker/outbox/OutboxPublisher.java` — scheduled distribution work
- `backend/launchforge-event-worker/src/main/java/dev/launchforge/eventworker/projection/ProjectionReconciler.java` — scheduled reconciliation
- `backend/launchforge-event-worker/src/main/java/dev/launchforge/eventworker/LaunchForgeEventWorkerApplication.java` and its `application.yml` — scheduling enabled without isolation
- `docs/06_SDK_ARCHITECTURE.md`, `docs/07_REALTIME_AND_EVENTING.md`, and `docs/13_PERFORMANCE_CAPACITY.md` — analytics isolation requirement

Required correction evidence: use distinct bounded execution resources for analytics and
configuration work, retain bounded ClickHouse I/O, and add an integration test in which ClickHouse
does not respond while outbox publication and reconciliation continue within their SLO.

#### P15-03 — The Helm production path does not completely model Kafka or Redis transport security

P15-01 added secret-backed, process-specific Redis ACL usernames/passwords to the chart and
application configuration. The chart still exposes no Redis TLS settings and no Kafka TLS/SASL
settings or bounded `extraEnv` escape hatch. A typical TLS-only Redis service or authenticated
managed Kafka service therefore still cannot be configured through the documented chart contract
without modifying the chart.

Evidence:

- `deploy/helm/launchforge/values.yaml` — `external.kafka`, `external.redis`, and secret model
- `deploy/helm/launchforge/templates/management.yaml`
- `deploy/helm/launchforge/templates/config-edge.yaml`
- `deploy/helm/launchforge/templates/event-worker.yaml`
- the three deployable `application.yml` files under `backend/`
- `docs/09_SECURITY_PRIVACY.md` and `docs/12_DEVOPS_CICD.md` — production transport and secret expectations

Required correction evidence: retain the new Redis ACL identities, define explicit secret-backed
Kafka SASL/TLS and Redis TLS configuration, render it without secret values, validate it in
Helm/Compose tests, and demonstrate connections to authenticated TLS-enabled test services.

#### P15-04 — Required GitHub change and deployment controls are not active

The repository documents branch protection and `staging`/`production` GitHub Environments as owner
setup. The review queried the live repository on 2026-08-21: repository rulesets were empty, the
`main` branch-protection endpoint reported that the branch was not protected, and the environments
collection was empty. Direct pushes to `main` remain possible and the protected promotion workflow
cannot exercise its intended approval boundary.

This is accurately disclosed in `PROJECT_STATUS.md`; the finding is an operational release blocker,
not a misleading code claim.

Evidence:

- `.github/workflows/ci.yml`, `.github/workflows/release.yml`, and `.github/workflows/promote.yml`
- `docs/24_RELEASE_SUPPLY_CHAIN.md`
- live GitHub API responses for `Yashraj-Rathore/LaunchForge` on 2026-08-21

Required correction evidence: configure a `main` ruleset with the six documented required checks,
review/CODEOWNERS and history protections, protect release tags, create reviewed `staging` and
`production` environments with OIDC/secrets, and complete one same-digest staged promotion.

### Medium

#### P15-05 — The 64 KiB JSON variation limit is measured differently across publication and SDKs

The contract says 64 KiB of canonical UTF-8. Management checks Java `String.length()` in
`FlagDefinition.FlagValue` and `JacksonSnapshotCodec`; the Java and TypeScript SDK parsers measure
UTF-8 bytes. A multibyte JSON value can pass publication while being rejected by both SDKs, causing
clients to retain last-known-good configuration or use defaults.

Evidence: `docs/04_API_AND_CONTRACTS.md`; `FlagDefinition.java`; `JacksonSnapshotCodec.java`;
Java `SnapshotParser.java`; TypeScript `packages/core/src/snapshot.ts`.

#### P15-06 — Data-plane configuration permits snapshots above the normative 5 MiB ceiling

The Control API and both SDKs enforce 5 MiB, but `ConfigEdgeProperties` and
`DistributionProperties` accept operator values through 8 MiB. Defaults are lower and the normal
producer blocks oversized publications, so this is not a normal-path defect. It is still a contract
split: imported, legacy, or compromised 5–8 MiB materialization can be projected/served and then
rejected by SDKs. The contract permits deployments to configure lower limits, not higher ones.

Evidence: `docs/04_API_AND_CONTRACTS.md`; `ControlPlaneService.java`; `ConfigEdgeProperties.java`;
`DistributionProperties.java`; both SDK snapshot parsers.

#### P15-07 — Browser analytics transport has no bounded request timeout

Browser bootstrap and stream requests use `AbortController`, but `sendAnalyticsBatch` supplies no
abort signal and `BrowserAnalyticsOptions` exposes no timeout. A fetch that never resolves keeps
`analyticsFlush` pending, makes explicit `flushAnalytics()` hang, and prevents later batches from
being flushed through that promise. The evaluation hot path remains local and the queue remains
bounded, which limits severity.

Evidence: `sdks/javascript/packages/browser/src/client.ts`, its tests, and the bounded analytics
transport requirement in `docs/06_SDK_ARCHITECTURE.md`.

#### P15-08 — Two tenant-owned relationship chains are not enforced by compound foreign keys

`audit_events.project_id` and `environment_id` are nullable additions without compound foreign keys
to the duplicated `organization_id`. `sdk_keys.rotated_from_id` references only global key ID and
does not prove that the predecessor belongs to the same organization/project/environment. Reviewed
application queries are tenant-scoped and integration tests deny cross-tenant direct-ID access, so
no API exploit was demonstrated; the database nevertheless permits invalid tenant lineage through
defects, migrations, or privileged/manual writes.

Evidence: Flyway `V1__tenancy_identity.sql`, `V2__flag_control_plane.sql`, and
`V3__server_sdk_keys.sql`; `docs/03_DOMAIN_AND_DATABASE.md` compound-ownership rule.

#### P15-09 — Organization onboarding is absent while the README says teams can create organizations

The README says teams can create organizations, projects, and environments. The implemented
organization controller lists organizations and manages members but has no organization create or
rename endpoint; local organizations come from deterministic seed/provisioning. Project and
environment creation are implemented. This is a product/API gap and a misleading capability claim,
not a tenancy defect.

Evidence: `README.md` capability list; `OrganizationController.java`; `docs/04_API_AND_CONTRACTS.md`;
MVP organization-management scope in `docs/01_PRODUCT_REQUIREMENTS.md` and
`docs/15_BACKLOG_AND_ACCEPTANCE.md`.

#### P15-10 — One invalid authoritative snapshot can starve reconciliation after its cursor

`ProjectionReconciler.reconcile` rethrows validation/materialization errors and advances the cursor
only after the whole page succeeds. A persistent corrupt current snapshot therefore prevents later
environments on that page and subsequent pages from being rebuilt. Failing visibly is preferable to
silently accepting poison, but the current behavior has an unbounded multi-tenant recovery blast
radius and no focused test.

Evidence: `ProjectionReconciler.java`, its repository ordering contract, and Redis-rebuild/failure
requirements in `docs/07_REALTIME_AND_EVENTING.md` and `docs/18_FAILURE_MODES_RUNBOOKS.md`.

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

P15-03, P15-08, and P15-11 are the unresolved security/tenant findings; P15-01 is resolved as
recorded above. No cross-organization API access was reproduced. Server-derived organization
scope, role checks, compound ownership on core
entities, SDK credential-class separation, hash-only server-key verification, CSRF/OIDC/session
boundaries, and privacy-safe request logging were traced in code and exercised by the 25-test
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

P15-05 and P15-06 are pre-evaluation snapshot acceptance/size compatibility gaps, not a difference
in rule evaluation meaning.

## Revision, event, and snapshot compatibility review

No ordering or schema-version defect was found in the normal publication pipeline. PostgreSQL
publication atomically creates an immutable higher revision, audit row, environment pointer, and
outbox row. Event consumers use environment ID as the key, revision as ordering authority, reject
unsupported schemas, and are idempotent for duplicate/stale delivery. Rollback creates a newer
revision. The full distribution integration test passed PostgreSQL → outbox → Kafka → Redis → Edge
→ SDK convergence and stale/duplicate/rebuild behavior.

Outstanding compatibility findings are P15-05 (UTF-8 value sizing) and P15-06 (5 MiB versus 8 MiB
configuration). The former P15-01 provenance defect is resolved as recorded above.

## Failure-mode gaps

- P15-02: analytics outage can delay configuration scheduled work.
- P15-07: a never-resolving browser analytics request has no timeout.
- P15-10: a poison authoritative row can starve later reconciliation.

Existing failure evidence remains strong for SDK last-known-good/default behavior, stream-to-poll
fallback, Redis loss/rebuild, duplicate/stale events, key revocation, atomic publication failure,
edge restart, rollback, and disabled analytics. Those tests do not close the gaps above.

## Test and operational-evidence gaps

Each P15 finding needs the focused regression evidence stated with it. In addition:

- the final checklist's clean-clone four-minute demo run has not been executed after this review;
- controlled load evidence is local and bounded, not production capacity proof;
- the release/promotion/restore workflows have not run against configured hosted environments;
- no chaos test holds ClickHouse indefinitely while asserting distribution progress;
- no multibyte boundary corpus tests management publication and both SDKs at 64 KiB;
- no browser test uses a never-resolving analytics fetch;
- no database test attempts cross-tenant audit or key-rotation lineage;
- no reconciliation test places a poison environment before healthy tenants in a page.

## README, resume, demo, and commercial-claim review

P15-09 is the material README overclaim. P15-12 is stale package metadata. The case-study throughput
and propagation numbers are correctly labeled as local controlled measurements, the demo/customer
names are fictional, pilot pricing is explicitly a hypothesis, and no customers, revenue,
production capacity, multi-region deployment, or completed hosted release are claimed. The
four-minute demo pauses are presentation pacing rather than artificial configuration latency.

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

No item below was implemented by Prompt 15. Approval should name one or more IDs before code or
configuration changes begin.

### Stage 0 — hosted-release blockers

1. **P15-01 (resolved 2026-08-21):** authenticated Redis materialization provenance and
   least-privilege ACLs established and regression-tested.
2. **P15-02:** isolate analytics and configuration schedulers; prove ClickHouse failure isolation.
3. **P15-03:** add secret-backed Kafka/Redis authentication and TLS to deployment contracts.
4. **P15-04:** configure and verify live GitHub rulesets, environments, and first staged promotion.

### Stage 1 — compatibility and tenant integrity

5. **P15-05:** enforce canonical UTF-8 bytes at management publication and add cross-SDK boundaries.
6. **P15-06:** share/cap the normative 5 MiB snapshot ceiling across all processes.
7. **P15-07:** add bounded browser analytics request timeouts and hanging-fetch coverage.
8. **P15-08:** add safe compound tenant/lineage constraints and migration tests.
9. **P15-11:** remove production-artifact database credential defaults.

### Stage 2 — resilience, product truth, and documentation

10. **P15-10:** isolate poison reconciliation records without silently accepting or losing them.
11. **P15-09:** implement organization onboarding/lifecycle or narrow the public capability claim.
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
