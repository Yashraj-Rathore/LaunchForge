# LaunchForge - AI Agent Prompt Sequence

Use **one prompt at a time**. Do not ask the implementation agent to build the whole platform in one task.

The canonical individual prompts live under `.ai-agent/prompts/`.

## Prompt order

0. `00_REPOSITORY_ASSESSMENT.md` - read/report/wait; no code.
1. `01_FOUNDATION.md` - LF-0001..0005.
2. `02_TENANCY_IDENTITY.md` - LF-0101..0105.
3. `03_FLAGS_CONTROL_PLANE.md` - LF-0201..0207.
4. `04_JAVA_SDK_EVALUATOR.md` - LF-0301..0307.
5. `05_CONFIG_EDGE_STREAMING.md` - LF-0401..0406.
6. `06_JAVASCRIPT_REACT_SDKS.md` - LF-0501..0505.
7. `07_ADMIN_CONSOLE.md` - LF-0601..0606.
8. `08_KAFKA_REDIS_DISTRIBUTION.md` - LF-0701..0706.
9. `09_ANALYTICS_CLICKHOUSE.md` - LF-0801..0805, optional.
10. `10_SECURITY_HARDENING.md` - LF-0901..0906.
11. `11_RELIABILITY_PERFORMANCE.md` - LF-1001..1006.
12. `12_CONTAINERS_HELM.md` - LF-1101..1104.
13. `13_CICD_SUPPLY_CHAIN.md` - LF-1201..1205.
14. `14_DEMO_PILOT.md` - LF-1301..1305.
15. `15_FINAL_ARCHITECTURE_REVIEW.md` - review only.

## Standard single-issue prompt

> Implement issue `[LF-XXXX]` from `docs/15_BACKLOG_AND_ACCEPTANCE.md`. Read `AGENTS.md`, `templates/definition-of-done.md`, the issue, all directly relevant numbered docs and ADRs before changing code. Restate scope and affected modules, implement only that issue, add/update tests, run required validation, update documentation/status/changelog when appropriate, and report files changed, commands/results, security/architecture considerations, and remaining risks. Do not begin the next issue.

## Defect-fix prompt

> Investigate defect `[description]`. Read the relevant source-of-truth docs first. Reproduce the defect with an automated failing test where practical before changing behavior. Identify root cause, make the smallest safe correction, run targeted and full relevant regression tests, update docs if the contract changes, and report evidence plus remaining risk. Do not implement unrelated backlog work.

## Performance-regression prompt

> Investigate performance regression `[description]`. Preserve correctness first. Reproduce with the existing benchmark/load methodology on documented hardware/configuration, profile before optimizing, identify bottleneck with evidence, make the smallest justified change, rerun correctness and benchmark suites, and report raw before/after artifacts and limitations. Do not weaken safety, consistency, or tests to improve a number.

## Security-review prompt

> Review `[scope]` against `docs/09_SECURITY_PRIVACY.md`, `AGENTS.md`, and applicable ADRs. Do not patch automatically. Report exploitable or unsafe conditions ranked by severity, reproduction/evidence, affected trust boundary, and a minimal remediation plan. Never print discovered secrets.

## Architecture-review prompt

> Review the implementation against `docs/02_SYSTEM_ARCHITECTURE.md`, `docs/05_FLAG_EVALUATION_ENGINE.md`, `docs/07_REALTIME_AND_EVENTING.md`, and all ADRs. Do not refactor automatically. Return violations ranked by severity with file paths/evidence and a staged correction plan.

## Important evaluator-change rule

Any change to:

- rule semantics;
- type coercion;
- operator meaning;
- rollout hashing;
- snapshot interpretation;

must update/review the algorithm/schema version and shared Java/JavaScript golden vectors. Do not change one SDK independently.

## Important distribution-change rule

Kafka/Redis/SSE optimizations may not change the externally visible fact that:

- PostgreSQL published revision is authoritative;
- SDK evaluation is local;
- SSE is a revision hint;
- snapshot fetch converges to the latest valid revision;
- Redis is rebuildable;
- clients retain last-known-good behavior.
