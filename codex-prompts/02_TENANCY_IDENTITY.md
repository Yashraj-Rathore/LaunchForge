# Codex Prompt 02 - Tenancy and Identity

Implement **LF-0101 through LF-0105 only**.

Build:

- Organization and membership domain;
- PostgreSQL/Flyway tenant persistence;
- server-derived organization context;
- OIDC BFF/session using the documented provider-neutral design and local Keycloak reference;
- Owner/Admin/Developer/Viewer policies;
- CSRF/session security foundation;
- fictional authenticated seed and minimal React shell.

Required:

- cross-tenant read/write denial integration tests;
- authentication/role tests;
- Playwright access smoke;
- no bearer/refresh token localStorage design.

Do not implement feature flags or SDK keys yet.

Run validation, update docs/status/changelog, report, and stop.
