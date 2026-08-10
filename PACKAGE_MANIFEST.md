# Package Manifest

This repository combines the complete implementation specification and Codex handoff package with the implemented M0 foundation. Product behavior remains intentionally deferred to its owning milestones.

## Root control documents

- `README.md`
- `AGENTS.md`
- `CODEX_START_HERE.md`
- `CODEX_PROMPT_SEQUENCE.md`
- `CODEX_MASTER_IMPLEMENTATION_SPEC.md` - generated after package build
- `PROJECT_STATUS.md`
- `IMPLEMENTATION_CHECKLIST.md`
- `CHANGELOG.md`

## Architecture/product docs

Numbered files under `docs/00` through `docs/21`.

They cover:

- product requirements;
- architecture;
- database/domain;
- APIs/contracts;
- evaluator;
- SDKs;
- real-time distribution;
- frontend;
- security;
- testing;
- observability;
- DevOps;
- performance;
- milestones;
- issue backlog/acceptance;
- demo;
- commercialization;
- runbooks;
- technology baseline;
- interview talk track;
- non-goals.

## ADRs

Eight initial ADRs under `docs/decisions/`.

## Codex prompts

Sixteen ordered prompt files under `codex-prompts/`.

## Templates

- definition of done;
- PR template;
- issue template;
- environment/config examples;
- snapshot example;
- golden-vector placeholder;
- demo seed placeholder.

## Engineering scripts

- `eng/sync_master_spec.py`
- `eng/validate_docs.py`

## Implementation directories

- `backend/`
- `frontend/`
- `sdks/`
- `contracts/`
- `demos/`
- `deploy/`

`backend/`, `frontend/`, and `sdks/` now contain the bounded M0 build/workspace shells. Other directories, and product behavior inside these shells, remain reserved until their corresponding milestone.
