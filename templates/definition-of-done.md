# Definition of Done

An issue is done only when all applicable items are true.

## Scope

- implemented only the assigned issue(s);
- no future-milestone feature creep;
- acceptance criteria satisfied;
- architecture rules preserved.

## Code

- readable, intentionally named, no unexplained cleverness;
- external input validated;
- no hardcoded secrets/tenant IDs/production URLs;
- no unnecessary dependency;
- concurrency/cancellation/resource lifecycle handled where relevant.

## Tests

- unit tests for behavior;
- integration tests for persistence/auth/eventing/external boundaries as applicable;
- cross-tenant denial test for new tenant-owned data;
- golden vectors for evaluator semantic changes;
- E2E for critical user flow where applicable;
- defect fixes include a reproducing failing test when practical.

## Validation

Run and report exact relevant commands, eventually including:

- backend formatting/static analysis;
- backend build/test;
- frontend lint/test/build;
- schema/contract validation;
- integration/E2E;
- docs validator.

## Security/privacy

- authorization reviewed;
- secret/logging exposure reviewed;
- request/data limits considered;
- browser/server key boundary preserved;
- no new sensitive context collection without explicit requirement.

## Documentation

- relevant source-of-truth doc updated;
- `CHANGELOG.md` updated for material behavior;
- `PROJECT_STATUS.md` updated after completion;
- commands/config examples updated.

## Report

Codex returns:

1. scope completed;
2. files changed;
3. tests/commands and outcomes;
4. architecture/security notes;
5. known risks;
6. next recommended issue, **without implementing it**.
