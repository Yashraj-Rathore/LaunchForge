# JavaScript Browser

`@launchforge/js-browser` owns bootstrap, conditional polling, revision-only SSE, immutable snapshot
activation, in-memory last-known-good behavior, and local evaluation. It delegates all evaluation and
snapshot validation to `@launchforge/js-core`.

```ts
import { LaunchForgeBrowserClient } from '@launchforge/js-browser';
import { createEvaluationContext } from '@launchforge/js-core';

const client = new LaunchForgeBrowserClient({
  baseUrl: 'https://edge.example',
  clientKey: import.meta.env.VITE_LAUNCHFORGE_CLIENT_KEY,
  initialContext: createEvaluationContext('canada-pro-user', {
    country: 'CA',
    plan: 'pro',
  }),
});

await client.start(); // bounded bootstrap; throws if no last-known-good snapshot exists
const enabled = client.boolVariation('new-checkout', false);
```

`start()` performs a bounded initial fetch and then starts jittered conditional polling plus the SSE
reconnect loop. A rejected, malformed, stale, or unavailable response never replaces the current
snapshot. `close()` is idempotent and releases timers, the stream, and subscriptions.

The `lf_client_...` identifier is public, not a secret. It is sent in the browser endpoint path so
Config Edge can enforce that key's exact CORS origin allowlist during preflight. Requests always use
`credentials: 'omit'`; wildcard origins and credentialed CORS are unsupported. Client-visible flag
configuration, values, rules, rollout salts, and the client key itself are inspectable by end users.
Never put secrets in flags and never use a client-evaluated flag as an authorization decision.
