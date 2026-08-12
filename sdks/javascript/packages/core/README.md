# JavaScript Core

`@launchforge/js-core` is the framework-neutral algorithm-version-1 evaluator. It compiles and
validates immutable snapshots, evaluates all four flag types locally, and consumes the exact same
`contracts/golden-vectors/evaluator-v1.json` corpus as the Java SDK.

Numeric compatibility is deliberately narrower than Java's `long`: snapshot revisions and
mathematical integer JSON values must remain in JavaScript's safe-integer range. Non-integer finite
binary64 values use RFC 8785 / ECMAScript number serialization, negative zero is normalized to zero,
and JSON object property order is not semantically significant. Strings are never trimmed,
case-folded, or Unicode-normalized.

```ts
import {
  createEvaluationContext,
  evaluateBoolean,
  parseSnapshot,
} from '@launchforge/js-core';

const snapshot = parseSnapshot(snapshotJson);
const context = createEvaluationContext('canada-pro-user', {
  country: 'CA',
  plan: 'pro',
});
const detail = evaluateBoolean(snapshot, 'new-checkout', context, false);
```

Evaluation is synchronous and performs no network, database, disk, or JSON parsing work.
