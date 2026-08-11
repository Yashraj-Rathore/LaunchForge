# Fictional Spring Storefront

This application demonstrates the framework-independent LaunchForge Java SDK in a Spring Boot
service. It evaluates two fictional flags locally:

- `new-checkout` as a boolean;
- `checkout-theme` as a string.

Each response includes `snapshotRevision`, making a published change visible without redeploying
the application. The client performs bounded blocking bootstrap, then uses the immutable in-memory
last-known-good snapshot. Streaming is enabled by default and conditional polling remains active as
a fallback. Configuration is not a secrets mechanism; never put secrets in flag values.

## Interactive live-update flow

1. Start PostgreSQL and the Control API, then start Config Edge on its default port `8082`. The
   Control API and edge must receive the same uncommitted `LAUNCHFORGE_SDK_KEY_PEPPER`.
2. Through an authenticated Control API session, create the `new-checkout` boolean flag and the
   `checkout-theme` string flag in a fictional development environment, publish revision 1, then
   create a server SDK key for that environment. Copy the returned plaintext key immediately; list
   operations expose metadata only.
3. Start the storefront with that key:

```powershell
$env:LAUNCHFORGE_BASE_URI = 'http://localhost:8082'
$env:LAUNCHFORGE_SDK_KEY = '<one-time-server-sdk-key>'
$env:LAUNCHFORGE_STREAMING = 'true'
./mvnw.cmd -pl demos/spring-demo -am package
java -jar demos/spring-demo/target/launchforge-spring-demo-0.1.0-SNAPSHOT-exec.jar
```

4. Read the current locally evaluated result:

```powershell
Invoke-RestMethod 'http://localhost:8080/demo/customer-123?plan=pro'
```

5. Change either flag and publish. Repeat the request without restarting the storefront. The flag
   result and `snapshotRevision` advance after the revision event triggers an authoritative
   conditional snapshot fetch.
6. For the kill-switch proof, disable `new-checkout`, publish again, and verify it becomes `false`
   at a newer revision.
7. Stop Config Edge and repeat the storefront request. Local evaluation continues at the last
   validated revision. Restarting the storefront while the edge is unavailable fails within
   `LAUNCHFORGE_BOOTSTRAP_TIMEOUT`, which is intentional for this blocking-bootstrap example.

## Reproducible automated proof

The M4 PostgreSQL/WebFlux/Java SDK integration test publishes a newer kill-switch revision, waits
for stream-driven convergence, revokes the key, and proves the SDK retains the last-known-good
value:

```powershell
./mvnw.cmd -pl tests/integration-tests -am verify -Pintegration "-Dit.test=ConfigEdgePostgresIT" "-Dfailsafe.failIfNoSpecifiedTests=false"
```

This command requires a running Docker engine for its PostgreSQL Testcontainer. Unit coverage for
the demo response and exposed revision runs in the normal Maven build.
