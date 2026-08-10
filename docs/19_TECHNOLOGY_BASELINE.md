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
| ClickHouse | current stable when M8 begins | Optional analytics only |
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
| PostgreSQL | `18.4`; image `postgres:18.4-bookworm`; manifest `sha256:d9c83446333daec3f0588cc709adb80c26090b7f9f0f7ec8d43c243385d79818` | M0 |
| Keycloak | `26.7.0`; image `quay.io/keycloak/keycloak:26.7.0`; manifest `sha256:0f198be292568439d700cdbfb893e69a6009bb43a94a06a945b1d3d506c76b13` | M1 |
| Apache Kafka | `4.3.1`; image `apache/kafka:4.3.1` | Re-verify in M7 |
| Redis | `8.2.8`; image `redis:8.2.8-bookworm` | Re-verify in M7 |
| Docker Engine | tested-tooling target `29.6.2` | M0 developer environment |
| Docker Compose | tested-tooling target `5.4.0` | M0 developer environment |
| Kubernetes | tested deployment target `1.36.2` | Re-verify in M11 |
| Helm | tested deployment target `4.2.3` | Re-verify in M11 |
| ClickHouse | intentionally not pinned | Select and verify only if M8 begins |

TypeScript 7.0 is not the initial pin because its first release does not expose the programmatic API needed by the surrounding tooling ecosystem; re-evaluate TypeScript 7 after 7.1 and full lint/test/build compatibility. Deferred services are documented candidates, not permission to add them before their milestone.

Official verification references:

- Java: <https://github.com/adoptium/temurin25-binaries/releases> and <https://www.oracle.com/java/technologies/javase/25-0-4-relnotes.html>
- Spring Boot: <https://spring.io/projects/spring-boot/> and <https://docs.spring.io/spring-boot/system-requirements.html>
- Maven/wrapper: <https://maven.apache.org/download.cgi> and <https://maven.apache.org/tools/wrapper/maven-wrapper-plugin/plugin-info.html>
- Node/pnpm: <https://nodejs.org/dist/index.json> and <https://github.com/pnpm/pnpm/releases>
- React/TypeScript: <https://react.dev/versions>, <https://github.com/Microsoft/TypeScript/releases>, and <https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/>
- Vite/Vitest/Playwright: <https://github.com/vitejs/vite/releases>, <https://github.com/vitest-dev/vitest/releases>, and <https://github.com/microsoft/playwright/releases>
- PostgreSQL: <https://www.postgresql.org/support/versioning/> and <https://hub.docker.com/_/postgres>
- Kafka/Redis/Keycloak: <https://kafka.apache.org/community/downloads/>, <https://download.redis.io/releases/>, <https://hub.docker.com/_/redis>, <https://www.keycloak.org/2026/07/keycloak-2670-released>, and <https://github.com/keycloak/keycloak/releases/tag/26.7.0>
- Docker/Kubernetes/Helm: <https://docs.docker.com/engine/release-notes/29/>, <https://github.com/docker/compose/releases>, <https://kubernetes.io/releases/>, and <https://github.com/helm/helm/releases>

LF-0003 resolved and recorded the PostgreSQL image manifest digest after a successful pull. Compose uses the readable tag and digest together, so a tag move cannot silently change the local database image. PostgreSQL 18 Compose volumes mount the image's version-appropriate data root at `/var/lib/postgresql`, not the older `/var/lib/postgresql/data` path.

LF-0103 re-verified Keycloak when M1 began and recorded the Quay manifest above. Local non-container validation used the official `keycloak-26.7.0.zip` release asset after verifying SHA-256 `e63bd0167199c0092b8a4d22cc137e6b7a70e0089070f6f7799b1be504b69a8a`; CI uses the digest-pinned container.

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

High-volume evaluation analytics are structurally different from transactional control-plane data. ClickHouse is an appropriate analytical store if analytics becomes a real feature, but adding it before core usage is unnecessary.

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

Prompt 0 decisions are recorded above. M0 pins only the technologies it actually introduces. Deferred entries must be re-verified in their owning milestone, and ClickHouse remains unselected unless optional analytics work starts. Exact image digests and dependency lockfiles are implementation artifacts and must be recorded by the issue that first resolves/downloads them.
