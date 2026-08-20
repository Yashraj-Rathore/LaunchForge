# 19 - Technology Baseline

## 1. Date of planning baseline

Initial architecture baseline: **2026-08-10**.

Exact patch versions must be verified from official release sources during `Prompt 00` before repository initialization and then pinned/recorded. Do not blindly copy versions from this planning document months later.

## 2. Core baseline

| Area | Planned line | Reason |
|---|---|---|
| Java | Java 25 LTS | Modern LTS; demonstrates current Java |
| Spring Boot | 4.1.x | Current Spring Boot generation at planning time |
| React | 19.2.x | Current React stable line at planning time |
| TypeScript | current supported stable | Strict frontend/SDK typing |
| PostgreSQL | 18.x | Authoritative relational store |
| Kafka | 4.3.x | Durable revision propagation |
| Redis | 8.2.x | Rebuildable current snapshot/rate state |
| ClickHouse | 26.7.x, pinned and verified in M8 | Optional analytics only |
| Keycloak | 26.7.x, pinned and verified in M1 | Local/reference OIDC |
| Docker | current supported | Local/runtime packaging |
| Kubernetes | current supported local/cloud target | Portfolio deployment |
| Helm | current supported | K8s packaging |
| OpenTelemetry | compatible current stable | Vendor-neutral telemetry |

### Verified pins

Verified against official release sources on **2026-08-10**; M1-owned tools were re-verified when introduced:

| Technology | Exact version/distribution | Introduction |
|---|---|---|
| JDK | Eclipse Temurin `25.0.4+7`, HotSpot | M0 |
| Spring Boot | `4.1.0` | M0 |
| Apache Maven | `3.9.16` through wrapper | M0 |
| Maven Wrapper Plugin | `3.3.4`, `only-script` wrapper type | M0 |
| Node.js | `24.19.0` LTS | M0 |
| pnpm | `11.21.0` through Corepack | M0 |
| React / React DOM | `19.2.7` | M0 |
| TypeScript | `6.0.3` | M0 |
| Vite | `8.2.1` | M0 |
| Vitest | `4.1.10` | M0 |
| Playwright | `1.62.1` | M1 |
| React Router DOM | `7.18.2` | M6 |
| TanStack React Query | `5.101.4` | M6 |
| Zod | `4.4.3` | M6 |
| PostgreSQL | `18.4`; image `postgres:18.4-bookworm`; manifest `sha256:d9c83446333daec3f0588cc709adb80c26090b7f9f0f7ec8d43c243385d79818` | M0 |
| Keycloak | `26.7.0`; image `quay.io/keycloak/keycloak:26.7.0`; manifest `sha256:0f198be292568439d700cdbfb893e69a6009bb43a94a06a945b1d3d506c76b13` | M1 |
| Apache Kafka | `4.3.1`; image `apache/kafka:4.3.1`; manifest `sha256:77e3df9054047a88b520d0cc46e16696d3b22022e1d580aeccd2632df6532837` | M7 |
| Redis | `8.2.8`; image `redis:8.2.8-bookworm`; manifest `sha256:2f7462b9e93e0a7ae2edf3a0a0babc8a4d29f8bfc50849b906b7caaef925edc1` | M7 |
| ClickHouse | `26.7.1.1315`; image `clickhouse:26.7.1.1315`; manifest `sha256:16537a9270ad63acbbee437ebbb826ea62b49690e863ae33e2fc5c16b7d9466c` | M8 |
| OpenTelemetry Collector Contrib | `0.158.0`; image `otel/opentelemetry-collector-contrib:0.158.0`; manifest `sha256:c5918f78992ee73b0d6f0e599423ac5ec52dd5d9726733114d6eca53d5a32ed5` | M10 |
| Prometheus | `3.13.1` LTS; image `prom/prometheus:v3.13.1`; manifest `sha256:3c42b892cf723fa54d2f262c37a0e1f80aa8c8ddb1da7b9b0df9455a35a7f893` | M10 |
| Grafana OSS | `13.0.2`; image `grafana/grafana:13.0.2`; manifest `sha256:5dad0df181cb644a14e13617b913b261a54f7d4fd4510721dba420929f35bea2` | M10 |
| k6 | `1.7.1`; image `grafana/k6:1.7.1`; manifest `sha256:4fd3a694926b064d3491d9b02b01cde886583c4931f1223816e3d9a7bdfa7e0f` | M10 |
| Docker Engine | tested-tooling target `29.6.2` | M0 developer environment |
| Docker Compose | tested-tooling target `5.4.0` | M0 developer environment |
| Kubernetes | tested deployment/rendering target `1.36.3` | M11 |
| Helm | `4.2.4`; CI image `alpine/helm:4.2.4`; manifest `sha256:76c375eed56144c68d6197c55bc5a4552fb42002190b796729901cbab3ae6e51` | M11 |
| kind | `0.32.0`; local node `kindest/node:v1.34.8`; manifest `sha256:02722c2dedddcfc00febf5d27fbeb9b7b2c14294c82109ff4a85d89ac9ba3256` | M11 local proof |
| Maven container builder | `maven:3.9.16-eclipse-temurin-25`; manifest `sha256:1b1fc6d0168ea616afd1c861d6f32ec37c9ec2ffe88a0351b3771dd4ad86b0d8` | M11 |
| Temurin JRE container | `eclipse-temurin:25-jre-noble`; manifest `sha256:fbcf915c585659b30eb766ada4d6d7cfc9ec1040bf521e95bf61b10a25af73db` | M11 |
| Node.js container builder | `node:24.19.0-bookworm-slim`; manifest `sha256:3638d9a6fe4030bd716be989438248074489337ba3275657f93595428be4fc03` | M11 |
| Nginx runtime | `nginx:1.31.2-alpine3.23`; manifest `sha256:54f2a904c251d5a34adf545a72d32515a15e08418dae0266e23be2e18c66fefa` | M11 |
| Trivy | `0.74.0`; image manifest `sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969` | M11 local image gate |

TypeScript 7.0 is not the initial pin because its first release does not expose the programmatic API needed by the surrounding tooling ecosystem; re-evaluate TypeScript 7 after 7.1 and full lint/test/build compatibility. Deferred services are documented candidates, not permission to add them before their milestone.

Official verification references:

- Java: <https://github.com/adoptium/temurin25-binaries/releases> and <https://www.oracle.com/java/technologies/javase/25-0-4-relnotes.html>
- Spring Boot: <https://spring.io/projects/spring-boot/> and <https://docs.spring.io/spring-boot/system-requirements.html>
- Maven/wrapper: <https://maven.apache.org/download.cgi> and <https://maven.apache.org/tools/wrapper/maven-wrapper-plugin/plugin-info.html>
- Node/pnpm: <https://nodejs.org/dist/index.json> and <https://github.com/pnpm/pnpm/releases>
- React/TypeScript: <https://react.dev/versions>, <https://github.com/Microsoft/TypeScript/releases>, and <https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/>
- Vite/Vitest/Playwright: <https://github.com/vitejs/vite/releases>, <https://github.com/vitest-dev/vitest/releases>, and <https://github.com/microsoft/playwright/releases>
- PostgreSQL: <https://www.postgresql.org/support/versioning/> and <https://hub.docker.com/_/postgres>
- Kafka/Redis/Keycloak: <https://kafka.apache.org/community/downloads/>, <https://hub.docker.com/r/apache/kafka/tags>, <https://download.redis.io/releases/>, <https://hub.docker.com/_/redis>, <https://www.keycloak.org/2026/07/keycloak-2670-released>, and <https://github.com/keycloak/keycloak/releases/tag/26.7.0>
- ClickHouse: <https://hub.docker.com/_/clickhouse/tags> and <https://hub.docker.com/_/clickhouse>
- OpenTelemetry Collector: <https://github.com/open-telemetry/opentelemetry-collector-releases/releases>
- Prometheus: <https://prometheus.io/download/>
- Grafana: <https://grafana.com/grafana/download/>
- k6: <https://grafana.com/docs/k6/latest/release-notes/>
- Docker/Kubernetes/Helm/kind: <https://docs.docker.com/engine/release-notes/29/>, <https://github.com/docker/compose/releases>, <https://kubernetes.io/releases/>, <https://github.com/helm/helm/releases>, and <https://github.com/kubernetes-sigs/kind/releases>
- Production image bases and scanner: <https://hub.docker.com/_/maven>, <https://hub.docker.com/_/eclipse-temurin>, <https://hub.docker.com/_/node>, <https://hub.docker.com/_/nginx>, and <https://github.com/aquasecurity/trivy/releases>

LF-0003 resolved and recorded the PostgreSQL image manifest digest after a successful pull. Compose uses the readable tag and digest together, so a tag move cannot silently change the local database image. PostgreSQL 18 Compose volumes mount the image's version-appropriate data root at `/var/lib/postgresql`, not the older `/var/lib/postgresql/data` path.

LF-0103 re-verified Keycloak when M1 began and recorded the Quay manifest above. Local non-container validation used the official `keycloak-26.7.0.zip` release asset after verifying SHA-256 `e63bd0167199c0092b8a4d22cc137e6b7a70e0089070f6f7799b1be504b69a8a`; CI uses the digest-pinned container.

LF-0803 re-verified the official ClickHouse image when M8 began on **2026-08-13**. Compose and the
real integration test use the readable `26.7.1.1315` tag together with the multi-platform manifest
digest above. The application uses Java's standard HTTP client for bounded inserts and aggregate
queries, so M8 adds no ClickHouse client-library dependency to the domain or SDK hot path.

LF-1001/LF-1002/LF-1004 re-verified the official collector, Prometheus LTS, Grafana OSS, and k6
releases on **2026-08-17**, then pulled and recorded the multi-platform manifest digests above.
LF-1003 uses JMH 1.37 in its own Maven module, following the OpenJDK recommendation to isolate the
benchmark harness from production artifacts. The Spring Boot OpenTelemetry starter remains managed
by the existing Spring Boot 4.1.0 dependency baseline.

LF-1101/LF-1103/LF-1104 re-verified Kubernetes 1.36.3, Helm 4.2.4, and kind 0.32.0 on
**2026-08-18**, and resolved every M11 container reference to the manifest shown above. The current
Temurin 25 JRE container still carries the 25.0.3 runtime while the host/CI compiler remains the
required 25.0.4+7 baseline; it uses the same Java 25 class-file level and is upgraded by digest when
the 25.0.4 JRE image is published and scanned. The local Docker Desktop test host exposes cgroup v1,
so kind's current Kubernetes 1.35/1.36 nodes reject kubelet startup. The proof therefore uses the
last release-compatible cgroup-v1 node, Kubernetes 1.34.8, while Helm lint/template targets current
Kubernetes 1.36.3. This compatibility exception is local-test infrastructure, not the production
cluster target.

### M0 build and quality pins

The M0 reactor and workspace additionally pin:

| Tool/library | Exact version |
|---|---|
| ArchUnit | `1.5.0` |
| Testcontainers | `2.0.5` |
| Spotless Maven Plugin | `3.9.0` |
| google-java-format | `1.36.1` |
| Checkstyle / Maven Checkstyle Plugin | `13.10.0` / `3.6.0` |
| Maven Compiler / Enforcer / Surefire / Failsafe | `3.15.0` / `3.6.3` / `3.5.6` / `3.5.6` |
| ESLint / Prettier | `10.8.1` / `3.9.6` |
| Java JSON Canonicalization | `io.github.erdtman:java-json-canonicalization:1.1` |
| JMH / Maven Shade Plugin | `1.37` / `3.6.2` |
| PostgreSQL JDBC | `42.7.12` |
| Netty | `4.2.16.Final` |

The root `pom.xml`, JavaScript package manifests, `pnpm-lock.yaml`, and SHA-pinned GitHub Actions are the executable source of truth for transitive and CI-tool versions.

M2 adds the RFC 8785 Java canonicalization implementation referenced by RFC 8785 itself. It is required because snapshot checksums need ECMAScript-compatible number rendering and deterministic property ordering; ordinary Jackson serialization is not a substitute for the checksum contract. The dependency is isolated to infrastructure and the framework-free domain remains dependency-free.

## 3. Backend libraries/categories

Prefer Spring-supported/default capabilities where possible:

- Spring Web MVC for management API;
- Spring WebFlux for Config Edge/SSE;
- Spring Security OAuth2/OIDC;
- Spring JDBC/JPA decision made per module—do not force ORM into evaluator;
- Flyway;
- PostgreSQL JDBC;
- Kafka client/Spring for Apache Kafka;
- Redis integration;
- Jackson;
- Bean Validation;
- Micrometer/OpenTelemetry bridge;
- Testcontainers;
- JUnit 5;
- AssertJ;
- ArchUnit;
- JMH.

The exact library list is finalized per issue. Avoid speculative dependencies.

## 4. Frontend

Expected:

- React;
- TypeScript;
- Vite;
- React Router;
- TanStack Query;
- Vitest;
- Testing Library;
- Playwright;
- ESLint/formatter.

A component library is optional. Prefer accessibility and coherent design over dependency count.

## 5. Why Java 25 instead of older Java

LaunchForge is a new portfolio project, not a legacy enterprise migration. A current LTS gives:

- modern language/runtime;
- long support horizon;
- stronger signal that Java knowledge is current.

Do not use preview language features in core APIs unless there is a compelling documented reason.

## 6. Why Spring Boot

The project needs:

- secure HTTP APIs;
- OIDC;
- validation;
- PostgreSQL;
- Kafka;
- Redis;
- observability;
- production health/config.

Spring Boot is appropriate and directly relevant to Java backend employment.

## 7. Why React

React is deliberately reused even though the user has React exposure already because the purpose of this project is to make **Java/Spring and distributed backend design** the new signal. A widely used frontend reduces project risk and makes the demo polished.

## 8. Why PostgreSQL

Needs:

- multi-tenant relational consistency;
- immutable revisions;
- audit;
- transactional publish/outbox;
- concurrency constraints.

PostgreSQL is system of record.

## 9. Why Kafka is delayed

Kafka is valuable when:

- revision distribution must survive consumer downtime;
- multiple projectors/edge nodes exist;
- replay/lag matter.

It is not required to prove domain/evaluator correctness, so it enters M7.

## 10. Why Redis is not source of truth

Redis gives fast current snapshot access and distributed ephemeral controls, but everything required to recover current configuration comes from authoritative persistent state/events.

## 11. Why ClickHouse is optional

High-volume evaluation analytics are structurally different from transactional control-plane data.
M8 introduces ClickHouse only behind the optional `analytics` Compose profile and disabled-by-default
application properties. PostgreSQL remains the configuration system of record, and ClickHouse
failure cannot affect management, distribution, or local evaluation.

## 12. Build strategy

Backend:

```text
Maven wrapper
```

Frontend/JS SDK:

```text
pnpm workspace managed through Corepack
```

Use one root `pnpm-lock.yaml`, one exact root `packageManager` declaration, and one package manager for all JavaScript/React packages, demos, and tools. Do not add npm or Yarn lockfiles.

## 13. Version update policy

- Renovate/Dependabot may propose changes later;
- patch/minor upgrades run full relevant tests;
- major upgrades require review/ADR if semantics change;
- snapshot/evaluator compatibility is more important than framework novelty;
- release artifacts record exact versions.

## 14. Pinning status

Prompt 0 decisions are recorded above. M0 pins only the technologies it actually introduces.
Deferred entries must be re-verified in their owning milestone; M8 has now selected and pinned
ClickHouse for optional analytics. Exact image digests and dependency lockfiles are implementation
artifacts and must be recorded by the issue that first resolves/downloads them.
