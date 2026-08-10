# 06 - SDK Architecture

## 1. Purpose

LaunchForge succeeds only if feature evaluation remains safe when the LaunchForge control plane is slow, unreachable, restarting, or being upgraded. The SDKs are therefore first-class product components, not thin HTTP wrappers.

The Java SDK is the reference implementation. The JavaScript SDK must implement the same evaluation semantics and pass the same language-neutral golden vectors.

## 2. SDK responsibilities

Each server-side SDK must:

- authenticate with a scoped SDK key;
- bootstrap an environment snapshot;
- validate snapshot schema and checksum before activation;
- hold an immutable in-memory snapshot;
- evaluate flags locally with no per-evaluation network request;
- maintain a last-known-good snapshot;
- subscribe to revision notifications when streaming is enabled;
- fall back to jittered polling when the stream is unavailable;
- expose safe caller-provided fallback values;
- provide deterministic reason metadata for debugging;
- never log SDK keys or raw context attributes by default;
- expose lifecycle methods so applications can close network resources cleanly.

## 3. Non-responsibilities

SDKs must not:

- store arbitrary application secrets;
- execute customer-provided code;
- call the management API;
- mutate flag configuration;
- implement business-specific user segmentation outside documented operators;
- make blocking network calls from the hot evaluation path;
- hide invalid variation/type mismatches.

## 4. Repository layout

```text
sdks/
  java/
    launchforge-java-sdk/
      src/main/java/...
      src/test/java/...
      README.md
  javascript/
    packages/
      core/
      browser/
      react/
    examples/
    README.md
contracts/
  config-snapshot.schema.json
  evaluation-context.schema.json
  analytics-event.schema.json
  golden-vectors/
```

The Java SDK must not depend on Spring. A separate optional Spring integration module may be added only after the pure Java SDK is stable.

## 5. Snapshot model

The runtime snapshot is the versioned immutable document defined normatively in `docs/03_DOMAIN_AND_DATABASE.md`. SDKs must not accept an alternate array-shaped flag model or alternate field names. An abbreviated valid shape is:

```json
{
  "schemaVersion": 1,
  "algorithmVersion": 1,
  "projectKey": "checkout-service",
  "environmentKey": "production",
  "revision": 42,
  "generatedAt": "2026-08-10T12:00:00Z",
  "flags": {
    "new-checkout": {
      "type": "boolean",
      "enabled": true,
      "clientVisible": true,
      "variations": [
        {"id": "off", "value": false},
        {"id": "on", "value": true}
      ],
      "offVariation": "off",
      "defaultVariation": "off",
      "rules": []
    }
  },
  "checksum": "<64 lowercase hexadecimal SHA-256 characters>"
}
```

Management-only details, user identities, audit records, internal database IDs, and secret material are excluded.

Checksum calculation, canonical JSON, numeric representation, server/browser projection behavior, and identifier rules are defined only in `docs/03_DOMAIN_AND_DATABASE.md` and `docs/05_FLAG_EVALUATION_ENGINE.md`.

## 6. Atomic snapshot swap

Parsing and validation occur off the hot path. After a new snapshot is valid, the SDK replaces one atomic reference:

```text
network bytes
   -> parse candidate
   -> validate schema/version/checksum
   -> compile evaluation form
   -> atomic reference swap
```

Readers never observe a partially updated configuration.

In Java, prefer an immutable compiled snapshot held by `AtomicReference<CompiledSnapshot>` or an equivalent safe-publication mechanism. Do not put a global write lock around every evaluation.

## 7. Java SDK public API

The exact package naming can be finalized during implementation, but the conceptual API is:

```java
LaunchForgeClient client = LaunchForgeClient.builder()
    .sdkKey(System.getenv("LAUNCHFORGE_SDK_KEY"))
    .baseUri(URI.create("https://config.launchforge.example"))
    .build();

EvaluationContext context = EvaluationContext.builder("user-123")
    .attribute("country", "CA")
    .attribute("plan", "pro")
    .build();

boolean enabled = client.boolVariation(
    "new-checkout",
    context,
    false
);

EvaluationDetail<Boolean> detail = client.boolVariationDetail(
    "new-checkout",
    context,
    false
);
```

`EvaluationDetail` should include at least:

- returned value;
- selected variation key when known;
- reason code;
- matched rule identifier when appropriate;
- snapshot revision;
- error kind when fallback was used.

Do not expose internal stack traces through the public evaluation result.

## 8. Evaluation reason codes

Use exactly the bounded algorithm-version-1 enum in `docs/05_FLAG_EVALUATION_ENGINE.md`: `FLAG_NOT_FOUND`, `FLAG_DISABLED`, `DEFAULT_VARIATION`, `RULE_MATCH`, `ROLLOUT_MATCH`, `MISSING_ROLLOUT_KEY`, `TYPE_MISMATCH`, `INVALID_CONFIG`, `SNAPSHOT_UNAVAILABLE`, and `ERROR_DEFAULT`.

Reason codes are useful in tests, debug tooling, and bounded metrics.

## 9. Initialization modes

Support explicit initialization behavior:

### Blocking bootstrap

The constructor/factory waits up to a bounded timeout for the first valid snapshot. If it cannot obtain one, creation fails with a documented exception.

Useful for services that must not start without feature configuration.

### Non-blocking bootstrap

Client starts immediately and returns caller defaults until a valid snapshot arrives.

Useful for applications that prioritize startup availability.

Non-blocking bootstrap is the default. Blocking bootstrap requires an explicit builder/factory option and a bounded caller-supplied or documented default timeout. In both modes, evaluation returns caller defaults with `SNAPSHOT_UNAVAILABLE` until a validated snapshot is active.

## 10. Streaming

Streaming is a notification mechanism, not the source of truth.

The stream event should be compact:

```text
event: revision
id: 43
data: {"revision":43}
```

On receipt:

1. compare with current revision;
2. ignore stale/duplicate notifications;
3. fetch the authoritative snapshot with a conditional request;
4. validate;
5. atomically activate;
6. persist durable last-known-good state when the optional Milestone 10 feature is configured.

The SDK must reconnect with exponential backoff plus jitter. It must not reconnect in a tight loop.

## 11. Polling fallback

When streaming is disabled or unhealthy, use bounded jittered polling.

Recommended behavior:

- conditional GET using ETag;
- configured minimum/maximum interval;
- random jitter to avoid synchronized fleets;
- no snapshot replacement on `304 Not Modified`;
- retain last-known-good on any transient failure.

## 12. Last-known-good behavior

In-memory last-known-good behavior is required in Milestone 3: a transient refresh failure or invalid newer snapshot never replaces the active valid snapshot.

Durable local-file persistence is deferred to LF-1005 in Milestone 10. When implemented, it optionally persists the most recently validated snapshot using an atomic write/rename pattern.

Startup order:

1. load and validate local LKG if configured;
2. make it active;
3. attempt remote bootstrap;
4. replace only with a newer valid remote revision.

Document the security implications of local snapshot persistence. Runtime configuration may itself be sensitive even though LaunchForge is not a secrets manager.

## 13. JavaScript and React SDKs

### JavaScript core

The JS core package owns:

- snapshot parsing;
- deterministic evaluator;
- rollout hashing;
- in-memory snapshot;
- polling/stream client appropriate to its runtime.

### Browser package

Browser environments require a public/mobile-style client key with limited scope. It must never use a server SDK key.

The browser package must assume the end user can inspect:

- the client key;
- delivered flag definitions;
- variation values.

Therefore do not deliver server-only sensitive rules or values to browser clients. A future relay/proxy pattern may provide stricter segmentation when needed.

### React wrapper

The React package should be thin:

- `LaunchForgeProvider`;
- `useFlag`;
- `useFlagDetail`;
- stable context update APIs.

It must not contain an independent evaluator.

## 14. Cross-language compatibility

Golden vectors are mandatory.

They must cover:

- every supported flag type;
- missing context;
- type mismatch;
- operator behavior;
- rule order;
- deterministic rollout boundaries;
- Unicode;
- empty strings;
- signed-looking and large numeric values;
- percentage allocation boundaries;
- malformed snapshots rejected consistently.

Both Java and JS implementations run against the same fixtures in CI.

## 15. Thread safety

The Java SDK is intended to be shared as a singleton application dependency.

Requirements:

- concurrent evaluations are safe;
- snapshot activation cannot expose partial state;
- listener callbacks cannot block the evaluator;
- shutdown is idempotent;
- client state has no unbounded queues;
- mutable evaluation context is not shared between calls.

## 16. Performance goals

Goals are not resume claims until measured.

The evaluator should be designed for:

- no network I/O on the hot path;
- no database access on the hot path;
- no JSON parsing on the hot path;
- minimal allocations after snapshot compilation;
- bounded rule traversal;
- deterministic behavior.

JMH benchmarks in Milestone 10 establish actual performance.

## 17. Compatibility policy

Before public release:

- define semantic versioning;
- document supported snapshot schema versions;
- retain backward compatibility for at least one previous snapshot version or explicitly document the upgrade contract;
- never silently reinterpret a previously valid rule/operator.

Breaking evaluator semantics require a schema or algorithm version change and updated golden vectors.
