# Deployment

Production deployment artifacts are intentionally deferred until M11. The root `compose.yaml` contains digest-pinned PostgreSQL plus optional M1 `identity` and `identity-seed` profiles for the local Keycloak reference. The identity services are local-development/test support, not production deployment guidance.

`deploy/local/keycloak/launchforge-realm.json` defines a public Authorization Code client with PKCE S256 and fictional operators without passwords. `deploy/local/keycloak/set-demo-passwords.sh` assigns the password supplied at runtime. `deploy/local/tenancy-seed.sql` is an opt-in fictional organization, membership, and read-only project seed.

Planned:

```text
deploy/
  docker/
  helm/
```

Production PostgreSQL/Kafka/Redis/ClickHouse are assumed to be managed/external dependencies unless a later ADR says otherwise.

Kubernetes must not block the core Java evaluator/SDK product.
