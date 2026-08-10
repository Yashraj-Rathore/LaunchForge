# 14 - Timeline and Milestones

## 1. Delivery philosophy

Milestones are dependency ordered, not calendar promises. Implement one issue at a time and do not skip foundational correctness to reach Kafka/Kubernetes sooner.

The project is intentionally structured so it becomes resume-worthy before every advanced component is finished.

## 2. Milestone summary

| Milestone | Theme | Issues | Portfolio state |
|---|---|---|---|
| M0 | Repository foundation | LF-0001..0005 | Clean engineering skeleton |
| M1 | Tenancy and identity | LF-0101..0105 | Secure multi-tenant control plane |
| M2 | Flags and publishing | LF-0201..0207 | Useful management MVP |
| M3 | Java evaluator + SDK | LF-0301..0307 | **First strong resume checkpoint** |
| M4 | Config Edge + live updates | LF-0401..0406 | Real-time developer platform |
| M5 | JS/React SDKs | LF-0501..0505 | Cross-language platform |
| M6 | React admin console | LF-0601..0606 | Demonstrable product |
| M7 | Kafka + Redis distribution | LF-0701..0706 | Distributed systems depth |
| M8 | Optional analytics | LF-0801..0805 | Experimentation insight |
| M9 | Security hardening | LF-0901..0906 | Production security evidence |
| M10 | Reliability + performance | LF-1001..1006 | Measured engineering evidence |
| M11 | Containers + Helm | LF-1101..1104 | Cloud-native packaging |
| M12 | CI/CD + supply chain | LF-1201..1205 | Release engineering |
| M13 | Demo + pilot readiness | LF-1301..1305 | Recruiter + commercial package |

## 3. M0 - Foundation

Outcome:

- Java multi-module Maven repository;
- architectural boundaries;
- local PostgreSQL;
- React workspace;
- pinned toolchain;
- CI skeleton;
- source-of-truth docs.

No flag product features.

## 4. M1 - Tenancy and identity

Outcome:

- organizations;
- memberships/roles;
- server-derived tenant context;
- OIDC/Keycloak local reference;
- secure browser session;
- seeded authenticated shell.

This milestone makes later data safely multi-tenant.

## 5. M2 - Flag management and publishing

Outcome:

- projects/environments;
- typed flags/variations;
- rule model;
- percentage rollout configuration;
- draft/published separation;
- immutable revisions;
- transactional audit/outbox;
- rollback-as-new-revision.

At this point the control-plane domain is real.

## 6. M3 - Java evaluator and SDK

Outcome:

- pure Java evaluator;
- deterministic SHA-256 bucket algorithm;
- golden vectors;
- Java SDK bootstrap;
- local typed evaluation;
- polling;
- in-memory LKG;
- Spring Boot demo app.

This is the **first recommended resume checkpoint** because it proves modern Java beyond CRUD.

## 7. M4 - Config Edge and streaming

Outcome:

- separate WebFlux edge;
- SDK-key auth;
- snapshot endpoint;
- ETag;
- SSE revision notifications;
- Java SDK stream client;
- failure/reconnect tests;
- visible live-update demo.

Still no Kafka/Redis required for correctness.

## 8. M5 - JavaScript and React SDKs

Outcome:

- shared TS evaluator;
- same golden vectors;
- browser-safe client model;
- React hooks/provider;
- React demo application.

This proves evaluator compatibility across languages.

## 9. M6 - React admin console

Outcome:

- polished flag workflow;
- rule builder;
- percentage rollout editor;
- context simulator;
- publish/revision/rollback UI;
- SDK key management;
- audit page;
- accessible E2E flows.

Now the project can be shown to a non-technical recruiter in under two minutes.

## 10. M7 - Kafka and Redis

Outcome:

- transactional outbox publisher;
- Kafka revision event;
- idempotent projector;
- Redis current materialization;
- Redis invalidation hints;
- multiple edge replicas;
- broker/cache failure drills.

This milestone adds distributed complexity **after** core semantics are already proven.

## 11. M8 - Analytics

M8 is an optional branch after M7. It does not block M9 security hardening, M10 reliability, or product adoption work. If analytics is skipped, analytics-specific controls in later milestones are not applicable.

Outcome:

- opt-in evaluation event contract;
- batch ingestion;
- ClickHouse;
- aggregate query API;
- minimal experiment/usage dashboard;
- analytics outage isolation.

Analytics cannot affect flag evaluation.

## 12. M9 - Security hardening

Security requirements in earlier milestones remain mandatory; M9 is additional hardening and evidence, not the first point at which security is implemented. M9 depends on the core through M7, not on optional M8.

Outcome:

- hardened key lifecycle;
- distributed limits;
- headers/CORS;
- audit retention;
- log privacy tests;
- threat model.

## 13. M10 - Reliability and performance

Outcome:

- OpenTelemetry;
- dashboards/alerts;
- JMH;
- HTTP/SSE load;
- durable local LKG;
- failure runbooks exercised.

This is where measured resume numbers may become legitimate.

## 14. M11 - Containers and Kubernetes

Outcome:

- production images;
- full Docker Compose;
- Helm;
- local K8s rolling/restart test.

## 15. M12 - CI/CD

Outcome:

- PR gates;
- scans;
- SBOM/provenance;
- staging promotion;
- protected production;
- rollback procedure.

## 16. M13 - Demo and pilot

Outcome:

- fictional deterministic demo seed;
- integration guides;
- recruiter-quality video/screenshots;
- case-study README;
- simple developer-tool pilot/commercial package.

## 17. Scope stop points

### Stop Point A - Resume MVP

Complete through M3.

Enough to list:

- Java 25;
- Spring Boot;
- PostgreSQL;
- deterministic evaluator;
- SDK;
- immutable publishing;
- Testcontainers.

### Stop Point B - Strong interview/demo build

Complete through M7.

Adds:

- React;
- JavaScript SDK;
- SSE;
- Kafka;
- Redis;
- multi-node distribution.

This is likely the best value-to-time ratio.

### Stop Point C - Portfolio flagship

Complete through M10/M13.

Adds measured performance, security, reliability, demo, and commercial proof.

M11/M12 can be completed based on time; they strengthen DevOps but should not delay job applications.

## 18. Status discipline

`PROJECT_STATUS.md` is the human-readable current status.

When an issue is completed:

1. tests must pass;
2. documentation must be updated;
3. changelog entry added when material;
4. status updated;
5. next issue is **not** automatically implemented.

## 19. No artificial schedule

Do not put fabricated "week 1/week 2" claims in the repository. Actual development cadence varies. Git history should show real progression.
