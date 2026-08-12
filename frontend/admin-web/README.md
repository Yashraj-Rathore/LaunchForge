# LaunchForge Admin Console

The M6 React console is the same-origin operator interface for LaunchForge. It uses the server-side
OIDC/BFF session and CSRF endpoint; it never stores bearer tokens or SDK credentials in browser
storage.

## Run

From the repository root with the pinned Node.js and pnpm versions:

```powershell
pnpm install --frozen-lockfile
pnpm --filter @launchforge/admin-web dev
```

Vite proxies management, login, and OIDC routes to `http://127.0.0.1:8080`. Start PostgreSQL,
Keycloak, and the Control API as described in the root README before using real authentication.

## Validate

```powershell
pnpm --filter @launchforge/admin-web lint
pnpm --filter @launchforge/admin-web typecheck
pnpm --filter @launchforge/admin-web test
pnpm --filter @launchforge/admin-web build
pnpm --filter @launchforge/admin-web test:e2e
```

The Playwright suite uses intercepted same-origin API responses so the critical production workflow,
stale-write behavior, one-time key lifecycle, audit safety, and role denial remain deterministic. The
repository-level OIDC smoke separately exercises the real Keycloak, PostgreSQL, and Control API
boundary.

## Security boundaries

- The URL selects context, but the API derives and enforces tenant access.
- All mutations fetch an in-memory CSRF token and send same-origin credentials.
- ETags protect mutable resources; stale failures do not discard local edits.
- The simulator sends bounded fictional context to the Java-backed endpoint; the server does not
  persist or log it.
- Server SDK secrets are displayed from create/rotate responses once and are never query-cached.
- Public browser client keys are visibly separate from trusted server credentials.
