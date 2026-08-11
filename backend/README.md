# Backend Workspace

Java/Spring modules initialized by Prompt 01 and extended through Prompt 05:

- `launchforge-domain` - JDK-only domain boundary;
- `launchforge-application` - use cases and ports, depending only on Domain;
- `launchforge-contracts` - versioned transport-contract and credential-format boundary;
- `launchforge-infrastructure` - management persistence and external-system adapters;
- `launchforge-control-api` - Spring MVC management BFF with OIDC/session security, publication, and server SDK-key lifecycle;
- `launchforge-config-edge` - independent Spring WebFlux data plane with SDK-key authentication, PostgreSQL-backed ETag snapshots, and bounded revision-only SSE;
- `launchforge-event-worker` - reserved worker boundary.

Config Edge depends on runtime contracts and its own data-plane JDBC adapter, never management
controllers or application services. PostgreSQL is authoritative in M4; Kafka and Redis remain
deferred to M7.
