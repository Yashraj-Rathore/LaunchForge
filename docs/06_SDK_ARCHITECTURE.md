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

The LF-0304/LF-0306 Java implementation uses `dev.launchforge.sdk`, non-blocking bootstrap by default, explicit `blockingBootstrap(Duration)`, 2-second connect and 5-second request defaults, and a uniformly jittered 25–35-second polling window. The polling bounds and both network timeouts are caller-configurable up to five minutes. One daemon scheduler serializes refresh work; validated newer revisions replace one atomic reference, while `304`, stale/same revisions, transient HTTP failures, and invalid candidates retain the active in-memory snapshot. `close()` is idempotent and closes scheduler and HTTP resources without discarding the readable in-memory last-known-good snapshot.

M4 adds opt-in `streaming(true)` against `GET /sdk/v1/stream` while retaining conditional polling.
Newer revision events trigger a coalesced authoritative snapshot fetch; stale/duplicate/malformed
hints do not activate configuration. Reconnect uses caller-configurable exponential backoff with
jitter (500 milliseconds through 30 seconds by default). A stream reconnect checks the current
snapshot so missed events converge, and `close()` interrupts the stream as well as polling/network
resources without discarding the readable in-memory last-known-good snapshot.

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
- strict evaluation-context construction;
- RFC 8785 checksum verification and immutable compiled snapshot types.

The M5 implementation is `@launchforge/js-core`. It has no React or transport dependency, performs
no I/O during evaluation, uses `BigInt` for the unsigned rollout prefix, and consumes
`contracts/golden-vectors/evaluator-v1.json` directly. JavaScript mathematical integers are limited
to the safe integer range; finite non-integers use ECMAScript binary64/RFC 8785 rendering, and
negative zero is normalized to zero.

### Browser package

Browser environments require a public/mobile-style client key with limited scope. It must never use a server SDK key.

The browser package must assume the end user can inspect:

- the client key;
- delivered flag definitions;
- variation values.

Therefore do not deliver server-only sensitive rules or values to browser clients. A future relay/proxy pattern may provide stricter segmentation when needed.

`@launchforge/js-browser` owns one immutable active snapshot, bounded bootstrap, conditional
jittered polling, streaming-fetch SSE, exponential reconnect with jitter, and in-memory
last-known-good behavior. It activates only checksum-valid snapshots with nondecreasing revisions;
same-revision/different-content, stale, oversized, or malformed candidates are rejected. The
constructor itself performs no I/O. `start()` awaits one bounded bootstrap attempt and starts the
background transports, evaluation returns `SNAPSHOT_UNAVAILABLE` before activation, and `close()` is
idempotent. A snapshot activation or explicit immutable context replacement notifies subscribers.

### React wrapper

The React package should be thin:

- `LaunchForgeProvider`;
- typed boolean/string/number/JSON value hooks;
- a matching detail hook for every type;
- stable context update APIs.

It must not contain an independent evaluator.

The provider creates and owns exactly one browser client unless a client is injected, starts it in
an effect, and releases its timers, stream, and subscriptions on final unmount. React development
Strict Mode's effect rehearsal does not permanently close the owned client. Callers should memoize
context objects; changing context is an intentional local reevaluation and rerender, never a remote
context upload.

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
