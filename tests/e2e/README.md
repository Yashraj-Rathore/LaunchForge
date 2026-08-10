# Browser end-to-end tests

The M1 Playwright smoke authenticates through the real local Keycloak reference, verifies the server-authorized organization shell, rejects cross-tenant URL manipulation, and confirms local logout invalidates the application session.

Run it only after starting PostgreSQL, Keycloak, and the seeded Control API as documented in the root README. Set `LAUNCHFORGE_E2E_PASSWORD` to the local demo password and run `pnpm test:e2e`; Playwright starts and stops the Vite development server automatically.
