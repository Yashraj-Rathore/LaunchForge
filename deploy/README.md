# Deployment

Production deployment artifacts are intentionally deferred until M11. The root `compose.yaml`
contains digest-pinned PostgreSQL, the optional M1 `identity` profile for the local Keycloak
reference, the M7 `distribution` profile for Kafka/Redis, and the M8 `analytics` profile for
ClickHouse. These services are local-development/test support, not production deployment guidance.

`deploy/local/keycloak/launchforge-realm.json` defines a public Authorization Code client with PKCE S256 and fictional operators whose local-only password is resolved from an environment placeholder during import. `deploy/local/tenancy-seed.sql` is an opt-in fictional organization, membership, and read-only project seed.

`deploy/local/clickhouse/init/001_analytics.sql` creates the optional analytics table with monthly
partitions and a 90-day TTL. Supply a local-only `LAUNCHFORGE_CLICKHOUSE_PASSWORD` and start it with
the already-required distribution dependencies:

```powershell
docker compose --profile distribution --profile analytics up -d --wait
```

Analytics remains disabled in Config Edge, Event Worker, and Control API until
`LAUNCHFORGE_ANALYTICS_ENABLED=true` is exported for those processes.

Planned:

```text
deploy/
  docker/
  helm/
```

Production PostgreSQL/Kafka/Redis/ClickHouse are assumed to be managed/external dependencies unless a later ADR says otherwise.

Kubernetes must not block the core Java evaluator/SDK product.
