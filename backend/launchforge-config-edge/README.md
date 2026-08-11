# LaunchForge Config Edge

Config Edge is the independent Spring Boot WebFlux data-plane process introduced in M4. It
authenticates environment-scoped server SDK keys, reads the current immutable revision from
PostgreSQL, and exposes:

- `GET /sdk/v1/snapshot` for the authoritative runtime snapshot, including ETag, revision,
  checksum, and schema-version headers;
- `GET /sdk/v1/stream` for heartbeat and revision-only server-sent events.

The stream is a notification channel, not an authority. After a newer revision notification, an
SDK retrieves and verifies `/sdk/v1/snapshot`. Conditional polling remains the fallback. Kafka and
Redis are intentionally not used in M4.

## Run locally

Start PostgreSQL and the Control API first so Flyway applies all migrations through V3. Give the
Control API and Config Edge the same uncommitted HMAC pepper:

```powershell
$env:LAUNCHFORGE_SDK_KEY_PEPPER = '<at-least-32-random-bytes>'
./mvnw.cmd -pl backend/launchforge-control-api,backend/launchforge-config-edge -am package
java -jar backend/launchforge-config-edge/target/launchforge-config-edge-0.1.0-SNAPSHOT-exec.jar
```

Config Edge defaults to port `8082`. Database connection variables and the optional bounded SSE
settings are listed in the repository `.env.example` and `src/main/resources/application.yml`.
Plaintext SDK keys are returned only by the authenticated Control API create/rotate operation;
Config Edge receives them only through `Authorization: LF-SDK <key>`.

## Validate

```powershell
./mvnw.cmd -pl backend/launchforge-config-edge -am test
./mvnw.cmd -pl tests/integration-tests -am verify -Pintegration "-Dit.test=ConfigEdgePostgresIT" "-Dfailsafe.failIfNoSpecifiedTests=false"
```

The integration command requires a running Docker engine for its PostgreSQL Testcontainer.
