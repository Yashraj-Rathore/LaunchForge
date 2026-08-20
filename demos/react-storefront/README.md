# Northstar Commerce React Storefront

This fictional storefront demonstrates the LaunchForge React SDK with browser-safe public
configuration. It uses one client-visible boolean flag, `new-checkout`:

- `canada-pro-user` matches the ordered `country == CA AND plan == pro` rule;
- `us-free-user` misses the rule and deterministically hashes to rollout bucket `50000`;
- `demo-26` misses the rule and hashes to bucket `29240`, so it enters when the rollout moves
  deliberately from 10% to 50%;
- the anonymous context omits the rollout key and safely receives the default;
- a later disabled revision acts as a kill switch and rerenders the connected app without redeploy.

For the complete local demo, including the seeded Control API and Config Edge, run from the
repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File eng/demo.ps1 -Action Start
```

Then open `http://127.0.0.1:5174`. The script prints the fictional local owner credential and all
demo URLs. Use `-Action Reset -ConfirmReset` to restore the exact revision-1 baseline.

## Run against Config Edge

Create a browser client key for the fictional Development environment with the exact origin
`http://localhost:5174`, then provide the public key at runtime:

```powershell
$env:VITE_LAUNCHFORGE_EDGE_URL='http://localhost:8081'
$env:VITE_LAUNCHFORGE_CLIENT_KEY='<public lf_client_ value>'
corepack pnpm --filter @launchforge/react-storefront-demo dev --host 127.0.0.1 --port 5174
```

The value is intentionally public but remains runtime configuration rather than source code. Never
put a server SDK key in `VITE_*`, JavaScript, HTML, or a browser bundle. Publish a newer revision or
disable `new-checkout`; the revision-only stream causes an authoritative snapshot refresh and the UI
updates automatically.

The application shows evaluation reason, revision, and rollout bucket. These diagnostics are safe
and bounded. Client configuration is inspectable, flags are not a secrets manager, and the checkout
display choice is not an authorization boundary.

## Automated browser flow

```powershell
corepack pnpm --filter @launchforge/react-storefront-demo test:e2e
```

The Playwright configuration starts a local, in-process Config Edge contract harness. The test uses
the production browser and React SDK packages to prove ordered targeting, both sides of the
deterministic rollout, missing-key fallback, a deliberate 10%-to-50% update, and an SSE-triggered
kill-switch update. The harness is enabled only by `LAUNCHFORGE_DEMO_TEST=true`; it is demo/test
infrastructure, not an alternate SDK evaluator or production endpoint.
