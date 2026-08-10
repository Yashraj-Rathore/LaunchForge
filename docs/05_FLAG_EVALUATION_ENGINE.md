# 05 — Flag Evaluation Engine

This is a correctness boundary. Java and TypeScript evaluators must independently implement these semantics and pass the same golden vectors.

## Inputs

- immutable published snapshot
- flag key
- typed evaluation context
- caller-provided default

Context has a non-empty canonical string `key` when percentage rollout uses the default subject attribute.

Algorithm version 1 context attributes are scalar string, number, or boolean values. Semantic versions are strings interpreted only by semantic-version operators. Explicit JSON `null` is treated as missing. List-valued context attributes are deferred; `IN`/`NOT_IN` compare one string attribute with a configured string list.

## Result

Return:

- typed value
- variation ID if known
- reason code
- matched rule ID where relevant
- published revision
- safe optional error metadata

The canonical bounded reason-code enum is:

```text
FLAG_NOT_FOUND
FLAG_DISABLED
DEFAULT_VARIATION
RULE_MATCH
ROLLOUT_MATCH
MISSING_ROLLOUT_KEY
TYPE_MISMATCH
INVALID_CONFIG
SNAPSHOT_UNAVAILABLE
ERROR_DEFAULT
```

Meanings:

- `DEFAULT_VARIATION` is the normal fallthrough when no rule matches and no rollout selects a value.
- `MISSING_ROLLOUT_KEY` returns the configured default variation because the selected rollout attribute was absent, null, empty, or not a string.
- `INVALID_CONFIG` returns the caller default when a flag in an otherwise activated snapshot cannot be evaluated safely.
- `SNAPSHOT_UNAVAILABLE` is produced by the SDK facade, not the pure evaluator, when no validated snapshot is active.
- `ERROR_DEFAULT` is the final bounded fallback for an unexpected internal evaluation failure; the public result never exposes a stack trace.

`TARGET_MATCH`, `ROLLOUT`, `FALLTHROUGH`, `CONTEXT_ERROR`, and `MALFORMED_FLAG` are not algorithm-version-1 reason codes. Equivalent cases map respectively to `RULE_MATCH`, `ROLLOUT_MATCH`, `DEFAULT_VARIATION`, a non-match or `MISSING_ROLLOUT_KEY`, and `INVALID_CONFIG`.

## Evaluation order

1. flag lookup; missing -> caller default / `FLAG_NOT_FOUND`
2. requested type check; mismatch -> caller default / `TYPE_MISMATCH`
3. disabled -> off variation / `FLAG_DISABLED`
4. ordered rules; first match wins
5. percentage rollout
6. configured default variation / `DEFAULT_VARIATION`
7. invalid runtime config -> caller default / safe error

Published invalid config should be prevented server-side, but SDK still fails safe.

## Rules

Conditions inside one rule are ANDed. OR is represented as multiple ordered rules for MVP.

Except for `EXISTS` and `NOT_EXISTS`, a missing or explicit-null attribute never matches, including negative operators such as `NOT_EQUALS` and `NOT_IN`. Operators are valid only for their declared attribute type; a different runtime type is a non-match, never a coercion.

### String
`EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`, `STARTS_WITH`, `ENDS_WITH`, `CONTAINS`, `EXISTS`, `NOT_EXISTS`

String comparisons are case-sensitive ordinal comparisons of the exact well-formed Unicode scalar sequence. They do not trim, normalize Unicode, or apply locale-specific case rules. `IN` means the scalar context string equals one member of the configured string list; `NOT_IN` is its inverse only when the attribute exists and is a string.

### Number
`EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `BETWEEN_INCLUSIVE`, `EXISTS`, `NOT_EXISTS`

### Boolean
`IS_TRUE`, `IS_FALSE`, `EXISTS`, `NOT_EXISTS`

### Semantic version
`SEMVER_EQ`, `SEMVER_GT`, `SEMVER_GTE`, `SEMVER_LT`, `SEMVER_LTE`

Invalid semantic version strings do not match and are never compared lexically.

Semantic-version operators implement SemVer 2.0.0 precedence. Build metadata does not affect precedence. Inputs with leading/trailing whitespace or syntax outside SemVer 2.0.0 are invalid and do not match.

## Type policy

No implicit coercion.

- `"10"` string != `10` number
- `"true"` string != `true` boolean
- missing attribute does not match `NOT_EQUALS`; use `NOT_EXISTS` when absence itself is intended

Algorithm-version-1 numbers use finite IEEE-754 binary64 semantics in both Java and TypeScript:

- reject NaN, positive/negative infinity, and numeric literals that overflow binary64;
- normalize negative zero to positive zero before storage/comparison;
- integer values outside JavaScript's safe integer range `[-9007199254740991, 9007199254740991]` are rejected;
- parse and compare as Java `double` / JavaScript `number`, never by the original decimal spelling;
- equality compares the normalized binary64 value; ordering uses ordinary finite numeric ordering;
- snapshot numeric serialization follows RFC 8785, so exponent and decimal spelling cannot vary by language.

JSON flag variation values must satisfy the same I-JSON/canonical-number restrictions recursively. SDK APIs return immutable JSON values or defensive copies so callers cannot mutate the active snapshot.

## Rule output

A matching MVP rule returns one variation. Percentage split inside a rule is deferred unless a later ADR changes it.

## Percentage rollout algorithm

Buckets: `0..99,999`.

Canonical UTF-8 material:

```text
<flagKey>\n<rolloutSalt>\n<subjectAttributeValue>
```

Hash with SHA-256.

Take the first 8 digest bytes as **unsigned big-endian 64-bit** and calculate:

```text
bucket = unsigned64(first8bytes(SHA256(material))) mod 100000
```

Variation weights are cumulative in declaration order.

Example:

```text
A weight 10000 -> buckets 0..9999
B weight 90000 -> buckets 10000..99999
```

Implement independently in Java and TypeScript. Do not use Java `hashCode`, JS string hash, random number generators, or language-specific unstable hashing.

Hash-input rules for algorithm version 1:

- the rollout attribute value must be a non-empty string; numbers and booleans are not stringified;
- use the string exactly as supplied, with no trimming, case folding, or Unicode normalization;
- all strings must be well-formed Unicode; reject unpaired UTF-16 surrogates at SDK/context boundaries;
- flag keys and rollout salts are canonical newline-free values under `docs/03_DOMAIN_AND_DATABASE.md`;
- Java interprets the first eight digest bytes with unsigned arithmetic (for example `Long.remainderUnsigned`); JavaScript uses `BigInt`, never `Number`, for the 64-bit intermediate.

## Missing rollout attribute

If the specified rollout attribute is missing, null, empty, non-string, or otherwise invalid:

- do not randomly assign
- skip rollout
- return configured default variation
- detail reason is `MISSING_ROLLOUT_KEY`

## Salt

Salt remains stable across ordinary allocation edits. A subject's bucket never changes while flag key, salt, and exact subject value remain unchanged. Its selected variation can still change when cumulative allocation boundaries move; cohort expansion is guaranteed only for an edit that monotonically extends that same variation's bucket range without moving its existing boundary.

Explicit “re-randomize cohort” creates a new salt and requires clear warning/audit/change reason.

## Publish validation

Before publish:

- unique variation IDs
- values match flag type
- off/default variations exist
- unique rule IDs
- valid operator/type combinations
- rule target variations exist
- rollout weights total exactly 100000
- rollout variation IDs exist
- bounded attributes/rules/JSON values
- bounded total snapshot
- supported schema only

## Java thread safety

Evaluator reads immutable snapshot from an `AtomicReference` or equivalent safe publication mechanism. Refresh validates a new snapshot off hot path and atomically swaps after success. Evaluation never locks on network refresh.

## TypeScript behavior

Treat snapshots as immutable. Refresh validates then replaces the current reference. React subscriptions notify after successful swap.

## Cross-SDK golden vectors

Cover:

- every operator
- missing attributes
- type mismatch
- semantic versions
- Unicode subject/flag material
- rollout boundary buckets
- salt changes
- disabled flags
- JSON values
- malformed snapshot rejection
- deterministic large sample

Any semantic change requires spec update, vectors update, and both SDK suites green.

## Performance rule

Local evaluation performs no network, DB, disk, or per-evaluation server logging. JMH measurements are recorded later; do not claim unmeasured latency.
