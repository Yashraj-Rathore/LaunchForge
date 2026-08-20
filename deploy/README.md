# Deployment

LaunchForge ships production-shaped containers, a profile-driven local Compose stack, and one
Helm chart. PostgreSQL remains authoritative; Kafka, Redis, OIDC, and optional ClickHouse are
external managed services in production. The repository contains no production credentials.

## Images

The five deployable images are `launchforge-management`, `launchforge-config-edge`,
`launchforge-event-worker`, `launchforge-web`, and the one-shot `launchforge-migrator`. SDKs are
packages and are never containerized. The Dockerfiles under `deploy/docker/` use digest-pinned
multi-stage bases, fixed non-root users, OCI labels, health checks for long-running processes, and
minimal runtime contents. Workload manifests add read-only roots, bounded writable `tmpfs` or
`emptyDir` mounts, dropped capabilities, and disabled privilege escalation.

Build local artifacts from the repository root:

```powershell
docker build -f deploy/docker/Dockerfile.migrator -t launchforge-migrator:local .
docker build -f deploy/docker/Dockerfile.java --build-arg MODULE=backend/launchforge-control-api --build-arg ARTIFACT=backend/launchforge-control-api/target/launchforge-control-api-0.1.0-SNAPSHOT-exec.jar --build-arg APP_PORT=8080 -t launchforge-management:local .
docker build -f deploy/docker/Dockerfile.java --build-arg MODULE=backend/launchforge-config-edge --build-arg ARTIFACT=backend/launchforge-config-edge/target/launchforge-config-edge-0.1.0-SNAPSHOT-exec.jar --build-arg APP_PORT=8082 -t launchforge-config-edge:local .
docker build -f deploy/docker/Dockerfile.java --build-arg MODULE=backend/launchforge-event-worker --build-arg ARTIFACT=backend/launchforge-event-worker/target/launchforge-event-worker-0.1.0-SNAPSHOT-exec.jar --build-arg APP_PORT=8083 -t launchforge-event-worker:local .
docker build -f deploy/docker/Dockerfile.web -t launchforge-web:local .
```

The web Dockerfile also exposes a non-runtime `validation` target that runs the complete workspace
format, lint, type-check, unit-test, and production-build gates in the pinned Node toolchain:

```powershell
docker build --target validation -f deploy/docker/Dockerfile.web -t launchforge-web-validation:local .
```

Release automation must set `OCI_CREATED`, `OCI_REVISION`, and `OCI_VERSION`, scan every image,
publish immutable digests, and promote those same digests. Building/publishing/SBOM/provenance and
environment promotion belong to M12; the M11 images do not imply a production release.

## Complete local stack

Copy `.env.example` to the ignored `.env` file and replace every placeholder with local-only
values. Generate the shared SDK-key pepper with at least 32 random bytes. Then render and start the
production-shaped platform:

```powershell
Copy-Item .env.example .env
docker compose --profile identity --profile distribution --profile platform --profile demo config --quiet
docker compose --profile identity --profile distribution --profile platform --profile demo up -d --build --wait
docker compose ps -a
```

`migrations` waits for healthy PostgreSQL and must exit successfully. The fictional `demo-seed`
and every Java workload wait for that completion; management also waits for OIDC/Redis, while edge
and worker wait for Kafka/Redis. The web container is published at `http://127.0.0.1:8080` only
after management and Config Edge are healthy. Keycloak accepts the same-origin callback at that
port. The seed contains only fictional local data.

Add optional analytics or observability without changing the base topology:

```powershell
docker compose --profile identity --profile distribution --profile analytics --profile observability --profile platform --profile demo up -d --build --wait
```

Analytics stays off unless `LAUNCHFORGE_ANALYTICS_ENABLED=true`; when enabled, the platform waits
for ClickHouse and reads its password only from the local environment. Stop containers while
preserving named data, or explicitly reset only this Compose project's local data:

```powershell
docker compose --profile identity --profile distribution --profile analytics --profile observability --profile platform --profile demo down
docker compose --profile identity --profile distribution --profile analytics --profile observability --profile platform --profile demo down --volumes
```

The second command permanently removes LaunchForge's local PostgreSQL, ClickHouse, Prometheus, and
Grafana named volumes. It does not represent an application or database rollback.

## Helm

The chart is `deploy/helm/launchforge`. It renders management, Config Edge, Event Worker/projector,
web, and a pre-install/pre-upgrade Flyway Job. The migration Job has a lower hook weight and Helm
must observe its successful completion before creating or replacing application workloads.

Create a values file outside the repository containing production endpoints and immutable image
digests. Create the referenced Kubernetes Secret through an approved secret manager or
external-secrets controller. It must contain the configured database password and SDK-key pepper;
when analytics is enabled it must also contain the ClickHouse password. Do not put secret values in
Helm values or `--set` history.

```powershell
helm lint deploy/helm/launchforge --strict
helm template launchforge deploy/helm/launchforge --namespace launchforge --values path/to/non-secret-values.yaml
helm upgrade --install launchforge deploy/helm/launchforge --namespace launchforge --create-namespace --values path/to/non-secret-values.yaml --wait --timeout 10m
```

Default values deliberately use `.invalid` external endpoints and example image repositories so
an operator must supply PostgreSQL, Kafka, Redis, OIDC, ingress, and image settings. Production
defaults include two management/edge/web replicas, zero-unavailable rolling updates, Config Edge
HPA, management/edge PDBs, dedicated token-free service accounts, probes, resources, ingress, and
NetworkPolicy examples. Enable analytics only after supplying its ClickHouse endpoint and secret.

Application rollback selects a previously proven compatible image digest and reruns Helm. It does
not reverse Flyway migrations. Product configuration rollback is separate and publishes a newer
immutable environment revision.

## Local Kubernetes resilience proof

`eng/prove_kind_resilience.ps1` builds or reuses the five `:prompt12` images, creates an isolated
kind cluster, starts only external Compose dependencies, installs the chart, and cleans up. It
proves migration-before-workload ordering, revision-1 local SDK evaluation, last-known-good during
an Edge outage, reconnect to revision 2, rolling replacement of all four workloads, and matching
PostgreSQL/Redis revision 2 state.

Prerequisites are the exact tool versions recorded in `docs/19_TECHNOLOGY_BASELINE.md`, Docker
Desktop, Java 25, and the Maven wrapper. Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File eng/prove_kind_resilience.ps1
```

Use `-SkipBuild` only after building the exact current sources. `-KeepCluster` retains the isolated
cluster and dependencies for diagnosis; otherwise cleanup is automatic. If retained, remove them
with:

```powershell
kind delete cluster --name launchforge-prompt12
docker compose --project-name launchforge-prompt12 --profile identity --profile distribution down --volumes --remove-orphans
```

The local proof uses fictional deterministic credentials only inside the isolated fixture. They
are not production credentials and must never be reused.
