# 25 - Integration Quick Starts

## 1. Clean local start

Prerequisites are the pinned Java, Node/pnpm, Docker, and Compose versions in
`docs/19_TECHNOLOGY_BASELINE.md`. From the repository root, one command creates an ignored `.env`
with fresh local-only values, builds the platform, migrates PostgreSQL, applies the deterministic
fictional seed, verifies health and snapshot delivery, and prints the URLs and local login:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File eng/demo.ps1 -Action Start
```

Open the admin console at `http://127.0.0.1:8080` and the real React storefront at
`http://127.0.0.1:5174`. The script prints the generated `owner` password; it exists only in the
ignored local `.env` file.

Stop while preserving data:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File eng/demo.ps1 -Action Stop
```

Restore the exact fictional baseline. This removes only the isolated `launchforge-demo` Compose
containers and named volumes, then starts and verifies them again:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File eng/demo.ps1 -Action Reset -ConfirmReset
```

## 2. Credential classes

| Credential | Where it belongs | What it can access |
|---|---|---|
| Human OIDC session | HttpOnly same-origin browser session | authorized management operations |
| Server SDK key | server environment variable or approved secret manager | one environment's server snapshot/stream |
| Browser client key | public browser runtime configuration | one client-visible projection from approved origins |

Create a one-time server SDK key from **SDK keys -> Server keys** in the seeded Development
environment. Store it immediately in `LAUNCHFORGE_SDK_KEY`; list operations never return it again.
The seeded browser key is intentionally public and is already compiled into the local fictional
storefront. Never place a server key in a `VITE_*` value, browser bundle, screenshot, log, or source
file. Flags are configuration, not secrets or authorization decisions.

## 3. Plain Java quick start

Install the current SDK artifact from this checkout:

```powershell
./mvnw.cmd -pl sdks/java/launchforge-java-sdk -am install
```

Add `dev.launchforge:launchforge-java-sdk:0.1.0-SNAPSHOT` to the application, then create one
long-lived client:

```java
import dev.launchforge.sdk.EvaluationContext;
import dev.launchforge.sdk.EvaluationDetail;
import dev.launchforge.sdk.LaunchForgeClient;
import java.net.URI;
import java.time.Duration;

try (LaunchForgeClient client = LaunchForgeClient.builder()
    .baseUri(URI.create(System.getenv("LAUNCHFORGE_BASE_URI")))
    .sdkKey(System.getenv("LAUNCHFORGE_SDK_KEY"))
    .streaming(true)
    .blockingBootstrap(Duration.ofSeconds(5))
    .build()) {
  EvaluationContext visitor = EvaluationContext.builder("canada-pro-user")
      .attribute("country", "CA")
      .attribute("plan", "pro")
      .attribute("userId", "maya-pro-01")
      .build();

  EvaluationDetail<Boolean> checkout =
      client.boolVariationDetail("new-checkout", visitor, false);
  System.out.printf("enabled=%s reason=%s revision=%d%n",
      checkout.value(), checkout.reason(), checkout.snapshotRevision().orElse(-1L));
}
```

Local settings:

```powershell
$env:LAUNCHFORGE_BASE_URI = 'http://127.0.0.1:8082'
$env:LAUNCHFORGE_SDK_KEY = '<one-time server SDK key>'
```

The evaluation call uses only the validated in-memory snapshot. Streaming is a revision hint and
polling remains available; neither transport runs on the evaluation hot path.

## 4. Spring Boot quick start

Register that same client once and let Spring close it:

```java
@Bean(destroyMethod = "close")
LaunchForgeClient launchForgeClient(
    @Value("${launchforge.base-uri}") URI baseUri,
    @Value("${launchforge.sdk-key}") String sdkKey) {
  return LaunchForgeClient.builder()
      .baseUri(baseUri)
      .sdkKey(sdkKey)
      .streaming(true)
      .blockingBootstrap(Duration.ofSeconds(5))
      .build();
}
```

The committed Spring integration is executable:

```powershell
./mvnw.cmd -pl demos/spring-demo -am package
java -jar demos/spring-demo/target/launchforge-spring-demo-0.1.0-SNAPSHOT-exec.jar --server.port=18080
Invoke-RestMethod 'http://127.0.0.1:18080/demo/canada-pro-user?country=CA&plan=pro'
```

The explicit application port avoids the Compose admin console on host port 8080.

## 5. Browser JavaScript quick start

Use a browser client key created for the exact application origin:

```ts
import { LaunchForgeBrowserClient } from '@launchforge/js-browser';
import { createEvaluationContext } from '@launchforge/js-core';

const client = new LaunchForgeBrowserClient({
  baseUrl: import.meta.env.VITE_LAUNCHFORGE_EDGE_URL,
  clientKey: import.meta.env.VITE_LAUNCHFORGE_CLIENT_KEY,
  initialContext: createEvaluationContext('canada-pro-user', {
    country: 'CA',
    plan: 'pro',
    userId: 'maya-pro-01',
  }),
  streaming: true,
});

await client.start();
const checkout = client.boolVariationDetail('new-checkout', false);
console.log(checkout.value, checkout.reason, checkout.snapshotRevision);
```

Call `client.close()` when the application owns the client lifecycle. Browser requests omit
credentials, and the key's exact CORS allowlist is enforced by Config Edge.

## 6. React quick start

The React package is a thin subscription layer over the browser client:

```tsx
const context = useMemo(
  () => createEvaluationContext(user.id, {
    country: user.country,
    plan: user.plan,
    userId: user.rolloutKey,
  }),
  [user.country, user.id, user.plan, user.rolloutKey],
);

const options = useMemo(() => ({
  baseUrl: import.meta.env.VITE_LAUNCHFORGE_EDGE_URL,
  clientKey: import.meta.env.VITE_LAUNCHFORGE_CLIENT_KEY,
  initialContext: context,
  streaming: true,
}), []);

<LaunchForgeProvider options={options} context={context}>
  <Checkout />
</LaunchForgeProvider>
```

Inside `Checkout`, call `useBooleanFlagDetail('new-checkout', false)`. The provider owns one client,
subscribes to snapshot activation, rerenders on a valid newer revision, and releases resources on
final unmount.

## 7. Local and hosted endpoints

| Purpose | Local demo | Hosted/private pilot |
|---|---|---|
| Admin/BFF | `http://127.0.0.1:8080` | deployment HTTPS origin |
| Config Edge | `http://127.0.0.1:8082` | deployment HTTPS edge origin |
| Northstar demo | `http://127.0.0.1:5174` | optional fictional demo origin |

Hosted endpoints must use HTTPS. Never copy a local key into another environment; issue a key
scoped to the target environment and rotate or revoke it after testing.

## 8. Troubleshooting

- `SNAPSHOT_UNAVAILABLE`: confirm the correct Edge URL/key class, key status, environment status,
  and bounded bootstrap timeout.
- Browser CORS denial: add the exact scheme/host/port to the browser key; wildcard and credentialed
  browser delivery are deliberately unsupported.
- A publish committed but the demo is unchanged: compare PostgreSQL/Redis revision diagnostics,
  outbox age, and the revision shown by the SDK; do not republish blindly.
- Stream disconnected: polling and last-known-good behavior remain active. Inspect Edge readiness,
  rate limits, and key revocation before changing retry settings.
- Port collision: change the matching `.env` host port; do not change only the SDK URL.
- Reset requested: use the explicit isolated reset command above, not a broad Docker volume delete.

## 9. Executable evidence

The copy/paste paths above are covered by the normal build and focused demo gates:

```powershell
./mvnw.cmd -pl demos/spring-demo,sdks/java/launchforge-java-sdk -am verify
corepack pnpm --filter @launchforge/react-storefront-demo test:e2e
corepack pnpm --filter @launchforge/react-storefront-demo build
python eng/generate_demo_seed.py --check
python -m unittest eng.tests.test_demo_seed
```

The full clean-start and real-system capture commands are documented in
`docs/16_DEMO_PORTFOLIO.md`.
