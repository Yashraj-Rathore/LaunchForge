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
launchforge-projector        # if separate from worker
launchforge-analytics        # later
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

The current M0/M1 workflow in `.github/workflows/ci.yml` implements the applicable subset: the Maven reactor and architecture gates, the real PostgreSQL Testcontainers tenancy/session tests, the complete frontend format/lint/typecheck/test/build suite, Compose rendering, documentation and JSON-template validation, and a real Keycloak/seeded-Control-API Playwright identity smoke. Later-milestone gates above are added only when their corresponding artifacts exist. Every third-party Action is SHA-pinned, and service images use readable tags plus immutable manifests.

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
