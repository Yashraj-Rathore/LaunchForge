# Backend Workspace

Java/Spring modules initialized by Prompt 01 and extended by Prompt 02:

- `launchforge-domain` — JDK-only domain boundary;
- `launchforge-application` — use cases and ports, depending only on Domain;
- `launchforge-contracts` — versioned transport-contract boundary;
- `launchforge-infrastructure` — adapters depending inward;
- `launchforge-control-api` — Spring MVC management BFF with OIDC login, PostgreSQL-backed sessions, CSRF, and organization/member endpoints;
- `launchforge-config-edge` — reserved runtime edge boundary;
- `launchforge-event-worker` — reserved worker boundary.

Planned modules are documented in `docs/02_SYSTEM_ARCHITECTURE.md`.

Prompt 02 implements LF-0101 through LF-0105: the framework-free organization/membership domain, server-derived tenant authorization, JDBC/Flyway persistence, role policies, and safe membership audit events. Flag, SDK, distribution, and analytics behavior remains deferred to its owning milestone.
