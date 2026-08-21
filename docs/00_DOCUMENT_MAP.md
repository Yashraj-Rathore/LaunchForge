# 00 — Document Map

| Document | Purpose |
|---|---|
| `01_PRODUCT_REQUIREMENTS.md` | users, jobs-to-be-done, scope, product rules, success criteria |
| `02_SYSTEM_ARCHITECTURE.md` | control plane/data plane, modules, deployables, dependency rules |
| `03_DOMAIN_AND_DATABASE.md` | entities, invariants, relational model, revisions, tenancy, and normative snapshot representation/checksum |
| `04_API_AND_CONTRACTS.md` | management, SDK, stream, analytics APIs and errors |
| `05_FLAG_EVALUATION_ENGINE.md` | normative evaluator semantics, reason codes, types, targeting and percentage rollout algorithm |
| `06_SDK_ARCHITECTURE.md` | Java/JS/React SDK behavior, cache, outage safety, streaming |
| `07_REALTIME_AND_EVENTING.md` | publication, outbox, Kafka, Redis, SSE and ordering |
| `08_FRONTEND_UX.md` | admin console workflows, accessibility and demo behavior |
| `09_SECURITY_PRIVACY.md` | auth, authorization, keys, privacy and threats |
| `10_TESTING_QUALITY.md` | test pyramid, conformance, integration, E2E, performance |
| `11_OBSERVABILITY_OPERATIONS.md` | logs, metrics, traces and operational signals |
| `12_DEVOPS_CICD.md` | Docker, Helm, migrations, release and rollback |
| `13_PERFORMANCE_CAPACITY.md` | benchmark methodology and targets |
| `14_TIMELINE_MILESTONES.md` | milestone order and dependencies |
| `15_BACKLOG_AND_ACCEPTANCE.md` | issue IDs and exact acceptance criteria |
| `16_DEMO_PORTFOLIO.md` | recruiter demo and honest resume evidence |
| `17_COMMERCIALIZATION.md` | pilots, pricing hypotheses and validation |
| `18_FAILURE_MODES_RUNBOOKS.md` | failure behavior and recovery |
| `19_TECHNOLOGY_BASELINE.md` | dated versions and pinning policy |
| `20_INTERVIEW_TALK_TRACK.md` | system-design explanations and interview questions |
| `21_NON_GOALS_AND_FUTURE.md` | deliberate exclusions and future options |
| `22_SECURITY_HARDENING_REVIEW.md` | M9 threat assessment, evidence, residual risks, and release checks |
| `23_RELIABILITY_PERFORMANCE_REPORT.md` | M10 telemetry, diagnostics, benchmark, load-harness, and failure-drill evidence |
| `24_RELEASE_SUPPLY_CHAIN.md` | M12 PR gates, scanning, immutable release evidence, protected promotion, and rollback |
| `25_INTEGRATION_QUICKSTARTS.md` | clean demo start plus tested Java, Spring, JavaScript, and React integration paths |
| `26_PILOT_PACKAGE.md` | narrow pilot profile, checklists, pricing hypotheses, feedback, and operating boundaries |
| `27_FINAL_ARCHITECTURE_REVIEW.md` | Prompt 15 ranked findings, evidence, release decision, and staged correction queue |

ADRs under `docs/decisions/` explain choices that must not be casually reversed.
