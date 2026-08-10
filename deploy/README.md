# Deployment

Production deployment artifacts are intentionally deferred until M11. The root `compose.yaml` contains digest-pinned PostgreSQL plus an optional M1 `identity` profile for the local Keycloak reference. The identity service is local-development/test support, not production deployment guidance.

`deploy/local/keycloak/launchforge-realm.json` defines a public Authorization Code client with PKCE S256 and fictional operators whose local-only password is resolved from an environment placeholder during import. `deploy/local/tenancy-seed.sql` is an opt-in fictional organization, membership, and read-only project seed.

Planned:

```text
deploy/
  docker/
  helm/
```

Production PostgreSQL/Kafka/Redis/ClickHouse are assumed to be managed/external dependencies unless a later ADR says otherwise.

Kubernetes must not block the core Java evaluator/SDK product.
