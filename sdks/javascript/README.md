# LaunchForge JavaScript Workspace

M5 implements the explicit package boundaries:

- `packages/core` — framework-neutral strict snapshot compiler and evaluator;
- `packages/browser` — browser transport, LKG, polling, and SSE boundary, depending on Core;
- `packages/react` — thin provider/hooks binding, depending on Browser/Core.

Core and Java execute the same frozen golden corpus. React contains no evaluator. Package READMEs
document the public-key/CORS boundary, lifecycle APIs, numeric compatibility, and context updates.
