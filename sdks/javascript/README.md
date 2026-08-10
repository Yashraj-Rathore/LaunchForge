# LaunchForge JavaScript Workspace

M0 reserves explicit package boundaries only:

- `packages/core` — framework-neutral evaluator/client boundary;
- `packages/browser` — browser transport/projection boundary, depending on Core;
- `packages/react` — future React binding, depending on Browser/Core.

No evaluator or SDK behavior is implemented before M5.
