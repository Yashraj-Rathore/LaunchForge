# 12 - DevOps, Containers, Kubernetes, and CI/CD

## 1. Sequence

Deployment sophistication must follow product correctness:

1. local processes;
2. PostgreSQL Testcontainers;
3. Docker Compose;
4. Kafka/Redis Compose;
5. production-shaped images;
6. local Kubernetes/Helm;
7. staging;
8. protected production.

Do not let Kubernetes block the evaluator or SDK milestones.

## 2. Container images

Deployable images:

```text
launchforge-management
launchforge-config-edge
launchforge-web
launchforge-event-worker     # distribution projector + optional analytics writer
```

Requirements:

- multi-stage;
- minimal runtime;
- non-root;
- fixed UID/GID where practical;
- read-only filesystem where compatible;
- health checks/probes;
- OCI labels;
- no build credentials/secrets;
- pinned base image digest for releases.

SDKs are packages, not containers.

## 3. Docker Compose

Final local stack may include:

- PostgreSQL;
- Kafka KRaft;
- Redis;
- optional ClickHouse;
- optional Keycloak;
- management API;
- config edge;
- worker/projector;
- React frontend;
- demo Spring app;
- demo React app;
- OpenTelemetry Collector;
- Prometheus/Grafana.

Profiles should allow developers to avoid starting ClickHouse/observability when not needed.

## 4. Database migrations

Use Flyway.

Rules:

- migration is an explicit release step;
- forward-compatible expand/contract strategy;
- workload does not race multiple migration attempts;
- rollback does not automatically reverse database migrations;
- destructive migrations require separate staged release.

## 5. Helm

Provide one chart or clear chart set under:

```text
deploy/helm/launchforge/
```

Values:

- image repositories/digests;
- replicas;
- resources;
- ingress;
- service accounts;
- secrets references;
- autoscaling;
- PDB;
- network policy toggles;
- OTel endpoint;
- external PostgreSQL/Kafka/Redis endpoints.

Production databases/brokers are external managed dependencies by default.

## 6. Kubernetes requirements

- readiness/liveness/startup probes;
- resource requests/limits;
- dedicated service accounts;
- least privilege;
- NetworkPolicy examples;
- PodDisruptionBudget for edge/management where replicas >1;
- horizontal scaling for Config Edge;
- graceful shutdown long enough to close SSE;
- termination handling tested;
- no plaintext secrets in values.

## 7. Release artifact immutability

Build once.

Promote the same image digests:

```text
build -> staging -> production
```

Do not rebuild production from the same Git tag.

## 8. GitHub Actions

### Pull request

- backend format/static/unit;
- architecture;
- PostgreSQL integration;
- JS/React lint/test/build;
- golden vectors;
- API/schema;
- secret scan;
- Compose/Helm validation;
- selected E2E.

### Release

- build images;
- image scan;
- generate SBOM;
- provenance attestation;
- publish immutable digests;
- deploy staging;
- migrations;
- smoke;
- gated production promotion.

Pin third-party Actions by full commit SHA.

The workflow in `.github/workflows/ci.yml` implements the complete M12 PR/main gate: Maven and
PostgreSQL integration, both evaluator corpora, frontend format/lint/typecheck/test/build and
Playwright flows, real Keycloak identity smoke, Compose/Helm/contracts, dependency review, proposed
Git-history secret scanning, and fixable HIGH/CRITICAL dependency/misconfiguration scanning. Every
third-party Action is SHA-pinned, repository validation rejects mutable Action references, and
validation/service images use readable tags plus immutable manifests.

## 9. Staging

Staging should prove:

- HTTPS;
- OIDC;
- management mutation;
- publish;
- outbox/Kafka;
- Redis projection;
- edge snapshot;
- SSE;
- Java demo;
- React demo;
- rollback.

Use fictional data.

## 10. Production gate

Require:

- protected GitHub environment;
- explicit approval;
- migration compatibility;
- staging smoke success;
- immutable artifact reference;
- documented rollback target;
- no outstanding critical/high unaccepted security findings.

### M9 security release checklist

Before promoting a release, record evidence that:

- backend format, static analysis, unit tests, and PostgreSQL/Redis integration tests pass;
- frontend lint, tests, and production build pass;
- cross-tenant path, query-filter, and body manipulation tests pass;
- wrong/revoked server and browser credential classes fail closed;
- snapshot, stream-start, active SSE, analytics, management, and key-lifecycle limits return the
  documented contract without exposing identifiers in metric labels;
- secure responses contain HSTS/CSP/nosniff/referrer/frame policy and browser CORS remains exact,
  non-credentialed, and absent from server endpoints;
- captured-log privacy tests contain no fake SDK secret, cookie/session, authorization value,
  email, query, request body, or evaluation context;
- audit export remains tenant-scoped and retention deletion remains disabled unless explicitly
  approved, previewed, count-confirmed, and within the policy cutoff;
- dependency/secret/image scans have no unaccepted critical/high finding;
- `docs/22_SECURITY_HARDENING_REVIEW.md` residual risks have an owner/decision and no new untracked
  security fix is bundled into the release;
- application rollback, configuration rollback, compromised-key response, Redis fallback, and SSE
  reconnect procedures are current.

## 11. Rollback

Application rollback:

- revert workload to previous compatible digest;
- do not reverse DB migrations automatically;
- verify edge snapshot compatibility.

Configuration rollback:

- performed through LaunchForge product workflow;
- creates new config revision.

These are separate operations.

## 12. Zero/low-downtime considerations

Management API:

- ordinary rolling deploy;
- compatible schema.

Config Edge:

- multiple replicas;
- connection draining;
- clients reconnect with jitter;
- SDK LKG prevents evaluation outage.

Kafka/projector:

- consumer group;
- idempotent revision application;
- rolling restart.

## 13. Infrastructure as code

For this project, Helm plus documented managed-service assumptions is sufficient initially.

Terraform may be added later if it demonstrates meaningful cloud architecture rather than duplicating PaymentOps portfolio work. It is intentionally not required for MVP.

## 14. Developer commands

The exact commands are established by Milestone 0. Desired developer experience:

```text
./mvnw verify
docker compose up -d postgres
npm ci
npm test
npm run build
```

Later:

```text
docker compose --profile distributed up
helm template ...
```

Document Windows PowerShell equivalents where commands differ.

M10 adds an optional `observability` Compose profile:

    docker compose --profile observability up -d --wait

It starts the pinned OpenTelemetry Collector, Prometheus, and Grafana definitions under
`deploy/local/observability/`. Applications continue to run outside Compose at ports 8080, 8082,
and 8083 and are scraped through `host.docker.internal`. Set the required local-only Grafana
password in `.env`; never commit it. Set `LAUNCHFORGE_OTEL_ENABLED=true` in each application
process to export traces to the collector. Prometheus metrics remain pull-based, and
`LAUNCHFORGE_OTLP_METRICS_ENABLED` stays false unless a separately reviewed metrics pipeline is
configured.

## 15. Repository secrets

GitHub Environments/Actions secrets only.

OIDC to cloud is preferred over long-lived cloud access keys.

Never commit `.env`, Keycloak admin passwords, DB passwords, signing keys, or SDK secrets.

## 16. Supply chain

Release gate:

- dependency lock/resolution reproducible enough for chosen ecosystem;
- Maven wrapper checksum policy;
- npm lockfile committed;
- container base pin;
- image scan;
- SBOM;
- provenance;
- signed/tagged release.

## 17. Disaster recovery assumptions

Document separately:

- PostgreSQL backups/PITR;
- Kafka retention/replay;
- Redis rebuild;
- ClickHouse retention/backup if analytics matters commercially.

LaunchForge code does not claim production DR until restore has been tested.

## 18. M11 container and Kubernetes implementation

LF-1101 through LF-1104 establish the production packaging boundary without implementing the M12
release pipeline. `deploy/docker/` contains one shared Java workload Dockerfile, a one-shot Flyway
migrator, and an Nginx-hosted same-origin web image. Builder/runtime images are digest-pinned,
runtime users are fixed and non-root, and release metadata is supplied through OCI build arguments.
The long-running images expose health checks; Compose and Kubernetes enforce read-only filesystems,
bounded writable mounts, dropped capabilities, and no privilege escalation. Local Trivy 0.74.0
scans of the final images found zero fixable HIGH/CRITICAL OS or JavaScript/JAR findings on
2026-08-18. Scan results are time-sensitive and must be regenerated for every release.

The root Compose file is now the production-shaped local topology. The `platform` profile adds an
explicit migration job plus management, Config Edge, Event Worker/projector, and web; `demo` adds
the fictional seed. Identity and distribution remain explicit profiles, while analytics and
observability stay optional. `service_completed_successfully` makes migration completion a hard
workload gate. Long-running services use dependency health gates and retain loopback-only host
publishing by default. Exact startup, shutdown, and destructive local-volume reset commands are in
`deploy/README.md`.

`deploy/helm/launchforge/` assumes external PostgreSQL, Kafka, Redis, OIDC, and optional ClickHouse.
Values hold endpoints, distinct Redis usernames, a non-secret materialization signing key ID, and
Secret key references. The runtime Secret supplies per-process Redis passwords, the worker-only
Ed25519 private key, and the Edge verification-key set. The pre-install/pre-upgrade migration Job blocks
workloads; application pods never run Flyway. Management, Edge, worker, web, and migration each use
dedicated service accounts with token automount disabled. The chart supplies startup/readiness/
liveness probes, resources, rolling strategies, ingress, optional NetworkPolicies, management/Edge
PDBs, and a Config Edge HPA. `enableServiceLinks: false` prevents Kubernetes-generated service
variables from colliding with LaunchForge's typed environment variables.

Validate the chart with the exact Helm baseline and render both production defaults and the local
kind override:

```powershell
helm lint deploy/helm/launchforge --strict
helm template launchforge deploy/helm/launchforge --namespace launchforge
helm template prompt12 deploy/helm/launchforge --namespace launchforge --values deploy/local/kind/values.yaml
```

The repository CI performs the same lint and two renders using a digest-pinned Helm image. M12 is
still responsible for image publishing, SBOM/provenance, immutable environment promotion, and
release gates.

The reproducible local proof is:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File eng/prove_kind_resilience.ps1
```

It uses kind 0.32.0 and the digest-pinned Kubernetes 1.34.8 node image because the validated local
Docker Desktop host exposes cgroup v1, which current kind node images no longer accept. Kubernetes
1.36.3 remains the production rendering target. The proof observes the Flyway hook completion
before workloads, evaluates revision 1 through the Java SDK, removes Config Edge while the SDK
continues from last-known-good, reconnects to revision 2, rolls all four application deployments,
and verifies that PostgreSQL and Redis still report revision 2. This is local resilience evidence,
not a production availability or capacity claim.

## 19. M12 release and supply-chain implementation

LF-1201 through LF-1205 add four workflow boundaries. `ci.yml` is the required PR/main quality and
security gate. `release.yml` accepts only an annotated semantic-version tag on a successful `main`
commit, builds the five images once, records GHCR digests, scans them, attaches SPDX SBOM/build
provenance, and deploys the immutable bundle to staging. `promote-production.yml` consumes that exact
successful workflow run behind the protected `production` environment. `rollback-production.yml`
accepts only a prior staging-tested bundle whose application supports the current database schema.

The versioned release manifest is generated and validated by `eng/release_manifest.py` from
`deploy/release/compatibility.json`; it is never assembled from mutable tags during promotion. Helm
release values are rendered from that manifest. A pre-migration, fail-closed schema ledger records
the target schema before Flyway, while the ordinary release ConfigMap records the successfully
deployed SHA, contract versions, and image references. Forward promotion enables the migration hook.
Application rollback disables it and proves the schema ledger is unchanged. Configuration rollback
continues to create a newer immutable product revision.

GitHub-hosted controls cannot be fully expressed in repository source. The repository owner must
configure the protected `main` ruleset, protected `v*` tags, required check names, CODEOWNERS review,
and `staging`/`production` Environments. Production requires a reviewer and must prevent self-review.
Provider OIDC is preferred; the portable baseline permits only a short-lived, narrowly scoped
environment kubeconfig until a provider-specific identity step is chosen. Values transported by an
environment secret contain no application secret values and reference Kubernetes Secrets managed
outside Git.

The full operating procedure, required environment variables, scanner exception policy,
attestation verification, staging smoke, promotion, and rollback commands are normative in
`docs/24_RELEASE_SUPPLY_CHAIN.md`.
