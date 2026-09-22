# AI Agent Start Here

You are implementing LaunchForge. Treat repository documentation as the source of truth.

## First assignment — do not code yet

### Step 1 — Read

Read completely:

- `AGENTS.md`
- `README.md`
- `docs/00_DOCUMENT_MAP.md`
- `docs/01_PRODUCT_REQUIREMENTS.md`
- `docs/02_SYSTEM_ARCHITECTURE.md`
- `docs/05_FLAG_EVALUATION_ENGINE.md`
- `docs/14_TIMELINE_MILESTONES.md`
- `docs/15_BACKLOG_AND_ACCEPTANCE.md`
- `docs/19_TECHNOLOGY_BASELINE.md`

Skim all ADRs and report any contradiction.

### Step 2 — Assess

Return:

1. Product understanding in at most 15 bullets.
2. Proposed repository/module structure.
3. Exact technology versions to pin, verified against official compatibility/release documentation.
4. Maven, Node and pnpm commands you expect to use.
5. Architecture dependency graph.
6. Data-model concerns or missing constraints.
7. Security/privacy concerns.
8. Requirement contradictions or ambiguous decisions.
9. Milestone dependency graph.
10. Exact scope of Prompt 1 / Milestone 0.
11. Validation commands for Java formatting/build/test, integration tests, frontend lint/typecheck/test/build, and local infrastructure.

### Step 3 — Wait

Do **not** write application code, initialize frameworks, or create product migrations during this assessment. Wait for approval to begin Prompt 1.

## Standard issue prompt

> Implement issue(s) `[IDs]` from `docs/15_BACKLOG_AND_ACCEPTANCE.md`. Read linked requirements and ADRs first. Provide a short plan, implement only those issues, add tests, run required validation, update source documentation/status if needed, regenerate `.ai-agent/master-implementation-spec.md`, and report files changed plus remaining risks. Do not start the next issue.

## Quality requirement

Every change must be understandable and reviewable by a human developer. Fast generation is not more important than correctness, security, deterministic behavior, interoperability, tests, and documentation.
