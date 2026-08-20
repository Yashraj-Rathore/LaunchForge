import { spawn, type ChildProcess } from 'node:child_process';
import { expect, test, type Page } from '@playwright/test';

test.skip(
  process.env.LAUNCHFORGE_RELEASE_SMOKE !== 'true',
  'This destructive fictional-data smoke runs only in the protected staging environment.',
);

interface ApiResult<T> {
  readonly status: number;
  readonly etag: string;
  readonly body: T;
}

interface Flag {
  readonly id: string;
  readonly key: string;
  readonly variations: readonly { readonly id: string; readonly key: string }[];
}

interface Draft {
  readonly enabled: boolean;
  readonly fallthroughVariationId: string;
  readonly offVariationId: string;
  readonly rules: readonly unknown[];
  readonly rollout: unknown | null;
}

interface Environment {
  readonly id: string;
  readonly version: number;
}

interface Revision {
  readonly revision: number;
}

interface IssuedKey {
  readonly key: { readonly id: string };
  readonly secret: string;
}

interface DemoResponse {
  readonly newCheckout: boolean;
  readonly searchRanking: string;
  readonly snapshotRevision: number;
}

function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

async function management<T>(
  page: Page,
  path: string,
  method: 'GET' | 'POST' | 'PUT' = 'GET',
  body?: unknown,
  ifMatch?: string,
): Promise<ApiResult<T>> {
  const result = await page.evaluate(
    async ({ requestPath, requestMethod, requestBody, expectedVersion }) => {
      const headers: Record<string, string> = { Accept: 'application/json' };
      if (requestMethod !== 'GET') {
        const csrfResponse = await fetch('/api/v1/auth/csrf', {
          credentials: 'same-origin',
          headers: { Accept: 'application/json' },
        });
        if (!csrfResponse.ok) throw new Error(`CSRF request failed: ${csrfResponse.status}`);
        const csrf = (await csrfResponse.json()) as { headerName: string; token: string };
        headers[csrf.headerName] = csrf.token;
      }
      if (requestBody !== undefined) headers['Content-Type'] = 'application/json';
      if (expectedVersion !== undefined) headers['If-Match'] = expectedVersion;
      const response = await fetch(requestPath, {
        method: requestMethod,
        credentials: 'same-origin',
        headers,
        ...(requestBody === undefined ? {} : { body: JSON.stringify(requestBody) }),
      });
      const text = await response.text();
      return {
        status: response.status,
        etag: response.headers.get('etag') ?? '',
        body: text ? (JSON.parse(text) as unknown) : null,
      };
    },
    { requestPath: path, requestMethod: method, requestBody: body, expectedVersion: ifMatch },
  );
  if (result.status < 200 || result.status >= 300) {
    throw new Error(`${method} ${path} failed with status ${result.status}`);
  }
  return result as ApiResult<T>;
}

async function findOrCreateFlag(
  page: Page,
  projectId: string,
  key: string,
  type: 'BOOLEAN' | 'STRING',
  firstValue: boolean | string,
  secondValue: boolean | string,
): Promise<Flag> {
  const listed = await management<readonly Flag[]>(page, `/api/v1/projects/${projectId}/flags`);
  const existing = listed.body.find((flag) => flag.key === key);
  if (existing) return existing;
  const created = await management<Flag>(page, `/api/v1/projects/${projectId}/flags`, 'POST', {
    key,
    name: `Release smoke ${key}`,
    type,
    clientVisible: false,
    variations: [
      { key: type === 'BOOLEAN' ? 'off' : 'baseline', name: 'Safe', value: firstValue },
      { key: type === 'BOOLEAN' ? 'on' : 'hybrid', name: 'Enabled', value: secondValue },
    ],
  });
  return created.body;
}

async function configureDraft(
  page: Page,
  flag: Flag,
  environmentId: string,
  enabled: boolean,
  fallthroughKey: string,
  offKey: string,
  summary: string,
): Promise<void> {
  const current = await management<Draft>(
    page,
    `/api/v1/flags/${flag.id}/environments/${environmentId}`,
  );
  const variation = (key: string) => {
    const found = flag.variations.find((candidate) => candidate.key === key);
    if (!found) throw new Error(`Flag ${flag.key} is missing variation ${key}`);
    return found.id;
  };
  await management<Draft>(
    page,
    `/api/v1/flags/${flag.id}/environments/${environmentId}`,
    'PUT',
    {
      enabled,
      fallthroughVariationId: variation(fallthroughKey),
      offVariationId: variation(offKey),
      rules: [],
      rollout: null,
      changeSummary: summary,
    },
    current.etag,
  );
}

async function publish(page: Page, projectId: string, environmentId: string): Promise<number> {
  const environments = await management<readonly Environment[]>(
    page,
    `/api/v1/projects/${projectId}/environments`,
  );
  const environment = environments.body.find((candidate) => candidate.id === environmentId);
  if (!environment) throw new Error('The protected staging environment is not in the project');
  const published = await management<Revision>(
    page,
    `/api/v1/environments/${environmentId}/publish`,
    'POST',
    { reason: 'Protected release workflow smoke' },
    String(environment.version),
  );
  return published.body.revision;
}

async function awaitSnapshot(edgeUrl: string, secret: string, revision: number): Promise<void> {
  await expect
    .poll(
      async () => {
        const response = await fetch(new URL('/sdk/v1/snapshot', edgeUrl), {
          headers: { Authorization: `LF-SDK ${secret}`, Accept: 'application/json' },
        });
        return response.ok ? Number(response.headers.get('x-launchforge-revision')) : -1;
      },
      { timeout: 60_000, intervals: [250, 500, 1_000, 2_000] },
    )
    .toBeGreaterThanOrEqual(revision);
}

function awaitStreamRevision(
  edgeUrl: string,
  secret: string,
  lastRevision: number,
): { readonly result: Promise<number>; readonly cancel: () => void } {
  const controller = new AbortController();
  const result = (async () => {
    const timeout = setTimeout(() => controller.abort(), 60_000);
    try {
      const response = await fetch(new URL('/sdk/v1/stream', edgeUrl), {
        headers: {
          Authorization: `LF-SDK ${secret}`,
          Accept: 'text/event-stream',
          'Last-Event-ID': String(lastRevision),
        },
        signal: controller.signal,
      });
      if (!response.ok || !response.body) throw new Error(`SSE request failed: ${response.status}`);
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let pending = '';
      for (;;) {
        const chunk = await reader.read();
        if (chunk.done) throw new Error('SSE stream closed before a newer revision');
        pending += decoder.decode(chunk.value, { stream: true }).replaceAll('\r\n', '\n');
        let boundary = pending.indexOf('\n\n');
        while (boundary >= 0) {
          const event = pending.slice(0, boundary);
          pending = pending.slice(boundary + 2);
          for (const line of event.split('\n')) {
            if (!line.startsWith('data:')) continue;
            const payload = JSON.parse(line.slice(5).trim()) as { revision: number };
            if (payload.revision > lastRevision) return payload.revision;
          }
          boundary = pending.indexOf('\n\n');
        }
      }
    } finally {
      clearTimeout(timeout);
    }
  })();
  return { result, cancel: () => controller.abort() };
}

function startJavaDemo(edgeUrl: string, secret: string): ChildProcess {
  return spawn('java', ['-jar', required('LAUNCHFORGE_SPRING_DEMO_JAR'), '--server.port=18080'], {
    env: {
      ...process.env,
      LAUNCHFORGE_BASE_URI: edgeUrl,
      LAUNCHFORGE_SDK_KEY: secret,
      LAUNCHFORGE_STREAMING: 'true',
      LAUNCHFORGE_BOOTSTRAP_TIMEOUT: '10s',
    },
    stdio: 'ignore',
  });
}

async function awaitDemo(
  expectedRevision: number,
  expectedCheckout: boolean,
): Promise<DemoResponse> {
  let latest: DemoResponse | null = null;
  await expect
    .poll(
      async () => {
        try {
          const response = await fetch('http://127.0.0.1:18080/demo/release-subject?plan=pro');
          if (!response.ok) return false;
          latest = (await response.json()) as DemoResponse;
          return (
            latest.snapshotRevision >= expectedRevision && latest.newCheckout === expectedCheckout
          );
        } catch {
          return false;
        }
      },
      { timeout: 60_000, intervals: [250, 500, 1_000, 2_000] },
    )
    .toBe(true);
  if (!latest) throw new Error('Java demo did not return a result');
  return latest;
}

test('staged release proves OIDC, publish, Edge, SSE, and Java demo convergence', async ({
  page,
}) => {
  test.setTimeout(300_000);
  const username = required('LAUNCHFORGE_E2E_USERNAME');
  const password = required('LAUNCHFORGE_E2E_PASSWORD');
  const projectId = required('LAUNCHFORGE_STAGING_PROJECT_ID');
  const environmentId = required('LAUNCHFORGE_STAGING_ENVIRONMENT_ID');
  const edgeUrl = required('LAUNCHFORGE_STAGING_EDGE_URL');
  const webUrl = required('LAUNCHFORGE_E2E_BASE_URL');
  const smokeId = required('LAUNCHFORGE_RELEASE_SMOKE_ID');
  if (!webUrl.startsWith('https://')) throw new Error('Staging web entry point must use HTTPS');
  if (!edgeUrl.startsWith('https://')) throw new Error('Staging Config Edge must use HTTPS');

  await page.goto('/');
  await page.getByRole('link', { name: 'Sign in with OpenID Connect' }).click();
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await expect(page.getByRole('heading', { name: 'Feature flags' })).toBeVisible();

  const checkout = await findOrCreateFlag(page, projectId, 'new-checkout', 'BOOLEAN', false, true);
  const ranking = await findOrCreateFlag(
    page,
    projectId,
    'search-ranking',
    'STRING',
    'lexical-v1',
    'hybrid-v2',
  );
  await configureDraft(
    page,
    checkout,
    environmentId,
    true,
    'on',
    'off',
    `Release ${smokeId} enables checkout`,
  );
  await configureDraft(
    page,
    ranking,
    environmentId,
    true,
    'hybrid',
    'baseline',
    `Release ${smokeId} selects hybrid search ranking`,
  );

  let keyId: string | undefined;
  let demo: ChildProcess | undefined;
  let cancelStream: (() => void) | undefined;
  try {
    const firstRevision = await publish(page, projectId, environmentId);
    const issued = await management<IssuedKey>(
      page,
      `/api/v1/environments/${environmentId}/sdk-keys`,
      'POST',
      { name: `release-smoke-${smokeId}`, expiresAt: null },
    );
    keyId = issued.body.key.id;
    const secret = issued.body.secret;
    await awaitSnapshot(edgeUrl, secret, firstRevision);

    demo = startJavaDemo(edgeUrl, secret);
    const firstDemo = await awaitDemo(firstRevision, true);
    expect(firstDemo.searchRanking).toBe('hybrid-v2');

    const stream = awaitStreamRevision(edgeUrl, secret, firstRevision);
    cancelStream = stream.cancel;
    await configureDraft(
      page,
      checkout,
      environmentId,
      false,
      'on',
      'off',
      `Release ${smokeId} kill switch`,
    );
    const secondRevision = await publish(page, projectId, environmentId);
    expect(secondRevision).toBeGreaterThan(firstRevision);
    await expect(stream.result).resolves.toBeGreaterThanOrEqual(secondRevision);
    const secondDemo = await awaitDemo(secondRevision, false);
    expect(secondDemo.snapshotRevision).toBeGreaterThanOrEqual(secondRevision);
  } finally {
    cancelStream?.();
    demo?.kill('SIGTERM');
    if (keyId) {
      await management<null>(page, `/api/v1/sdk-keys/${keyId}/revoke`, 'POST').catch(
        () => undefined,
      );
    }
  }
});
