# LaunchForge Java SDK

The Java SDK is a framework-independent Java 25 library. It bootstraps the immutable version-1
runtime snapshot, validates its schema and RFC 8785 projection checksum, swaps newer revisions
atomically, and evaluates flags locally without network, database, disk, Spring, or JSON parsing on
the evaluation hot path.

## Use

```java
try (LaunchForgeClient client = LaunchForgeClient.builder()
    .sdkKey(System.getenv("LAUNCHFORGE_SDK_KEY"))
    .baseUri(URI.create(System.getenv("LAUNCHFORGE_BASE_URI")))
    .streaming(true)
    .build()) {
  EvaluationContext context = EvaluationContext.builder("customer-123")
      .attribute("country", "CA")
      .attribute("plan", "pro")
      .build();

  boolean enabled = client.boolVariation("new-checkout", context, false);
  EvaluationDetail<String> theme =
      client.stringVariationDetail("checkout-theme", context, "classic");
}
```

Non-blocking bootstrap is the default. Until a valid snapshot is active, typed methods return the
caller default and detail methods report `SNAPSHOT_UNAVAILABLE`. Services that require
configuration before startup can opt in to a bounded wait:

```java
LaunchForgeClient client = LaunchForgeClient.builder()
    .sdkKey(System.getenv("LAUNCHFORGE_SDK_KEY"))
    .baseUri(URI.create(System.getenv("LAUNCHFORGE_BASE_URI")))
    .streaming(true)
    .blockingBootstrap(Duration.ofSeconds(5))
    .build();
```

The client sends `Authorization: LF-SDK <key>` only to `<baseUri>/sdk/v1/snapshot` and, when
enabled, `<baseUri>/sdk/v1/stream`. It uses bounded connect/request timeouts, performs conditional
GETs with ETag, and polls every 25-35 seconds by default.
`pollingInterval(minimum, maximum)` changes the bounded jitter range.

`streaming(true)` enables revision notifications without disabling polling fallback. A strictly
newer `event: revision` hint triggers a conditional authoritative snapshot fetch. Disconnects cause
an immediate convergence check followed by exponential reconnect with jitter; the default range is
500 milliseconds through 30 seconds and can be changed with
`streamReconnectBackoff(minimum, maximum)`. Stale, duplicate, malformed, or missed events are safe.
A transient failure, stale response, unsupported schema, or invalid checksum never replaces the
in-memory last-known-good snapshot. Calling `close()` is idempotent and interrupts polling/stream
resources; the active in-memory snapshot remains evaluable after closure.

The SDK key is accepted only through caller configuration and is never logged. Flag values are
configuration, not secrets.

## Typed APIs

- `boolVariation` / `boolVariationDetail`
- `stringVariation` / `stringVariationDetail`
- `numberVariation` / `numberVariationDetail`
- `jsonVariation` / `jsonVariationDetail`

`JsonValue` is an immutable RFC 8785 canonical representation. Details include a bounded reason
code, variation/rule identifiers when known, revision, rollout bucket when relevant, and a bounded
error kind without exception text or context data.

For direct evaluator use, parse trusted snapshot bytes with `SnapshotParser.parse` and call the
typed methods on `Evaluator`. Parsing and checksum verification belong off the hot path.

## Test and regenerate compatibility vectors

From the repository root:

```powershell
./mvnw.cmd -pl sdks/java/launchforge-java-sdk -am test
./mvnw.cmd -pl sdks/java/launchforge-java-sdk -am test "-Dtest=GoldenVectorCorpusTest" "-Dlaunchforge.updateGoldenVectors=true"
./mvnw.cmd -pl sdks/java/launchforge-java-sdk -am test "-Dtest=GoldenVectorCorpusTest"
```

The second command mechanically regenerates `contracts/golden-vectors/evaluator-v1.json`; the
third proves the frozen file exactly matches the reference generator and executes it. Review corpus
changes as a versioned contract change. Do not edit SHA-256 bucket outputs by hand.
