import { defineConfig } from '@playwright/test';

const externalBaseUrl = process.env.LAUNCHFORGE_E2E_BASE_URL;
const captureDemo = process.env.LAUNCHFORGE_DEMO_CAPTURE === 'true';

export default defineConfig({
  testDir: './specs',
  fullyParallel: false,
  retries: 0,
  reporter: 'line',
  outputDir: captureDemo ? '../../demos/demo-media/.playwright' : 'test-results',
  use: {
    baseURL: externalBaseUrl ?? 'http://localhost:5173',
    trace: externalBaseUrl ? 'off' : 'retain-on-failure',
    video: captureDemo ? { mode: 'on', size: { width: 1440, height: 900 } } : 'off',
    ...(captureDemo ? { viewport: { width: 1440, height: 900 } } : {}),
  },
  ...(externalBaseUrl
    ? {}
    : {
        webServer: {
          command: 'corepack pnpm --filter @launchforge/admin-web dev --host 127.0.0.1',
          cwd: '../..',
          url: 'http://127.0.0.1:5173',
          reuseExistingServer: true,
          timeout: 120_000,
        },
      }),
});
