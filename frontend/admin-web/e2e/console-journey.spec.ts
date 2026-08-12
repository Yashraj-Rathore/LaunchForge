import { expect, test } from '@playwright/test';
import type { Page, Route } from '@playwright/test';

const organizationId = '10000000-0000-0000-0000-000000000001';
const projectId = '20000000-0000-0000-0000-000000000002';
const environmentId = '30000000-0000-0000-0000-000000000003';
const flagId = '40000000-0000-0000-0000-000000000004';
const offId = '50000000-0000-0000-0000-000000000005';
const onId = '60000000-0000-0000-0000-000000000006';

test('creates, targets, simulates, publishes, and audits a production flag safely', async ({
  page,
}) => {
  const state = await installControlApi(page);

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Feature flags' })).toBeVisible();
  await expect(page.getByText('Production environment')).toBeVisible();

  await page.getByRole('button', { name: 'Create flag' }).click();
  await page.getByLabel('Flag key').fill('checkout-badge');
  await page.getByLabel('Display name').first().fill('Checkout badge');
  await page.getByRole('button', { name: 'Create safe draft' }).click();
  await expect(page.getByRole('heading', { name: 'Checkout badge' })).toBeVisible();
  expect(state.createdFlagBody).toMatchObject({ key: 'checkout-badge', type: 'BOOLEAN' });

  await page.getByRole('heading', { name: 'Checkout badge' }).click();
  await expect(page.getByText('Unpublished draft v1')).toBeVisible();
  await page.getByLabel('Display name').fill('Checkout badge local edit');
  await page.getByRole('button', { name: 'Save metadata' }).click();
  await expect(page.getByRole('alert')).toContainText(/local edits are preserved/u);
  await expect(page.getByLabel('Display name')).toHaveValue('Checkout badge local edit');
  await page.getByRole('button', { name: 'Add rule' }).click();
  await page.getByLabel('Rule name').fill('Canadian checkout');
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect.poll(() => state.savedDraftBody?.rules?.length).toBe(1);

  await page.getByLabel('Scalar attributes (JSON object)').fill('{"country":"CA"}');
  await page.getByRole('button', { name: 'Run deterministic evaluation' }).click();
  await expect(page.getByText('RULE_MATCH')).toBeVisible();
  await expect(page.getByText('Draft candidate 1')).toBeVisible();

  await page.getByRole('button', { name: 'Review and publish' }).click();
  await expect(page.getByRole('heading', { name: 'Publish to Production' })).toBeVisible();
  await page.getByLabel('Change reason / ticket reference').fill('LF-0705 reviewed rollout');
  await page.getByLabel(/I confirm this publishes/u).check();
  await page.getByRole('button', { name: 'Publish immutable revision' }).click();
  await expect(page.getByText('Revision 1 is durable')).toBeVisible();
  expect(state.publishReason).toBe('LF-0705 reviewed rollout');
  await page.getByRole('button', { name: 'Close publish review' }).click();

  await page.getByRole('link', { name: 'SDK keys' }).click();
  await page.getByRole('button', { name: 'Create key' }).click();
  await expect(page.getByTestId('one-time-secret')).toHaveText('srv_live_once_only');
  await page.getByRole('button', { name: 'I stored it securely' }).click();
  await expect(page.getByTestId('one-time-secret')).toHaveCount(0);
  await page.getByRole('button', { name: 'Rotate' }).click();
  await expect(page.getByTestId('one-time-secret')).toHaveText('srv_rotated_once_only');
  await page.getByRole('button', { name: 'I stored it securely' }).click();
  await page.getByRole('button', { name: 'Revoke' }).click();
  await expect(page.getByText('REVOKED', { exact: true })).toHaveCount(2);

  await page.getByRole('link', { name: 'Audit' }).click();
  await expect(page.getByText('ENVIRONMENT_PUBLISHED')).toBeVisible();
  await expect(page.getByText('LF-0705 reviewed rollout')).toBeVisible();
  await expect(page.locator('.audit-list')).not.toContainText(/country.*CA/iu);
  await expect(page.getByText('srv_live_once_only')).toHaveCount(0);
  await expect(page.getByText('srv_rotated_once_only')).toHaveCount(0);
});

test('keeps a viewer in an explicit read-only state', async ({ page }) => {
  await installControlApi(page, 'VIEWER');

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Feature flags' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create flag' })).toHaveCount(0);
  await page.getByRole('link', { name: 'SDK keys' }).click();
  await expect(page.getByText('Key management denied')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create key' })).toHaveCount(0);
});

interface ControlState {
  createdFlagBody?: Record<string, unknown>;
  savedDraftBody?: { readonly rules?: readonly unknown[] };
  publishReason?: string;
}

async function installControlApi(page: Page, role = 'OWNER'): Promise<ControlState> {
  const state: ControlState = {};
  let flagCreated = false;
  let serverKeys: readonly Record<string, unknown>[] = [];
  const flag = {
    id: flagId,
    projectId,
    key: 'checkout-badge',
    name: 'Checkout badge',
    type: 'BOOLEAN',
    clientVisible: false,
    status: 'ACTIVE',
    version: 1,
    variations: [
      { id: offId, key: 'off', name: 'Off', value: false },
      { id: onId, key: 'on', name: 'On', value: true },
    ],
  };
  const draft = {
    flagId,
    environmentId,
    enabled: true,
    fallthroughVariationId: offId,
    offVariationId: offId,
    rolloutSalt: 'stable-test-salt',
    rules: [],
    rollout: null,
    changeSummary: 'Initial draft',
    version: 1,
  };

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (path === '/api/v1/auth/me') {
      return json(route, {
        subject: 'operator-123',
        displayName: 'Avery Operator',
        organizations: [
          {
            id: organizationId,
            slug: 'northstar-commerce',
            name: 'Northstar Commerce',
            role,
          },
        ],
      });
    }
    if (path === '/api/v1/auth/csrf') {
      return json(route, { headerName: 'X-CSRF-TOKEN', token: 'browser-test-token' });
    }
    if (path === `/api/v1/organizations/${organizationId}/projects`) {
      return json(route, [
        {
          id: projectId,
          organizationId,
          key: 'storefront',
          name: 'Storefront',
          description: null,
          status: 'ACTIVE',
          version: 1,
        },
      ]);
    }
    if (path === `/api/v1/projects/${projectId}/environments`) {
      return json(route, [
        {
          id: environmentId,
          projectId,
          key: 'production',
          name: 'Production',
          kind: 'PRODUCTION',
          status: 'ACTIVE',
          currentRevision: state.publishReason === undefined ? 0 : 1,
          version: state.publishReason === undefined ? 1 : 2,
        },
      ]);
    }
    if (path === `/api/v1/projects/${projectId}/flags` && request.method() === 'GET') {
      return json(route, flagCreated ? [flag] : []);
    }
    if (path === `/api/v1/projects/${projectId}/flags` && request.method() === 'POST') {
      state.createdFlagBody = request.postDataJSON() as Record<string, unknown>;
      flagCreated = true;
      return json(route, flag, 201);
    }
    if (path === `/api/v1/flags/${flagId}` && request.method() === 'GET') {
      return json(route, flag, 200, { ETag: '"1"' });
    }
    if (path === `/api/v1/flags/${flagId}` && request.method() === 'PATCH') {
      return problem(route, 409, 'STALE_RESOURCE_VERSION', 'Version mismatch');
    }
    if (path === `/api/v1/flags/${flagId}/environments/${environmentId}`) {
      if (request.method() === 'PUT') {
        state.savedDraftBody = request.postDataJSON() as { readonly rules?: readonly unknown[] };
        return json(route, { ...draft, ...state.savedDraftBody, version: 2 }, 200, { ETag: '"2"' });
      }
      return json(route, draft, 200, { ETag: '"1"' });
    }
    if (path === `/api/v1/environments/${environmentId}/evaluate`) {
      return json(route, {
        flagKey: 'checkout-badge',
        value: true,
        variationId: onId,
        reason: 'RULE_MATCH',
        matchedRuleId: 'test-rule',
        rolloutBucket: null,
        errorKind: null,
        currentPublishedRevision: 0,
        candidateRevision: 1,
        configuration: 'DRAFT',
      });
    }
    if (path === `/api/v1/environments/${environmentId}/publish`) {
      state.publishReason = (request.postDataJSON() as { readonly reason: string }).reason;
      return json(route, revision(), 201);
    }
    if (path === `/api/v1/environments/${environmentId}/sdk-keys` && request.method() === 'GET') {
      return json(route, serverKeys);
    }
    if (path === `/api/v1/environments/${environmentId}/sdk-keys` && request.method() === 'POST') {
      const key = {
        id: '70000000-0000-0000-0000-000000000007',
        environmentId,
        name: 'Storefront server',
        fingerprint: 'sha256:test-fingerprint',
        status: 'ACTIVE',
        expiresAt: null,
        createdAt: '2026-08-12T12:00:00Z',
        lastUsedAt: null,
        revokedAt: null,
        rotatedFromId: null,
      };
      serverKeys = [key];
      return json(route, { key, secret: 'srv_live_once_only' }, 201);
    }
    if (/^\/api\/v1\/sdk-keys\/[^/]+\/rotate$/u.test(path)) {
      const previous = serverKeys[0];
      const replacement = {
        ...previous,
        id: '71000000-0000-0000-0000-000000000007',
        name: 'Storefront server',
        fingerprint: 'sha256:rotated-fingerprint',
        status: 'ACTIVE',
        rotatedFromId: previous?.id,
      };
      serverKeys =
        previous === undefined ? [replacement] : [{ ...previous, status: 'REVOKED' }, replacement];
      return json(route, { key: replacement, secret: 'srv_rotated_once_only' });
    }
    if (/^\/api\/v1\/sdk-keys\/[^/]+\/revoke$/u.test(path)) {
      serverKeys = serverKeys.map((key) => ({ ...key, status: 'REVOKED' }));
      return route.fulfill({ status: 204 });
    }
    if (path === `/api/v1/organizations/${organizationId}/audit`) {
      return json(route, [
        {
          id: '80000000-0000-0000-0000-000000000008',
          projectId,
          environmentId,
          actor: 'operator-123',
          action: 'ENVIRONMENT_PUBLISHED',
          targetType: 'ENVIRONMENT',
          targetId: environmentId,
          summary: 'Published immutable environment revision 1',
          reason: 'LF-0705 reviewed rollout',
          fromRevision: 0,
          toRevision: 1,
          correlationId: 'corr-browser-test',
          createdAt: '2026-08-12T12:01:00Z',
        },
      ]);
    }
    return route.fulfill({ status: 404, body: `No mock for ${request.method()} ${path}` });
  });
  return state;
}

function problem(route: Route, status: number, code: string, detail: string) {
  return route.fulfill({
    status,
    headers: { 'Content-Type': 'application/problem+json' },
    body: JSON.stringify({ code, detail, correlationId: 'corr-stale-test' }),
  });
}

function revision() {
  return {
    environmentId,
    revision: 1,
    sourceRevision: null,
    checksum: 'sha256:test-revision',
    snapshot: null,
    reason: 'LF-0705 reviewed rollout',
    actorSubject: 'operator-123',
    createdAt: '2026-08-12T12:01:00Z',
  };
}

function json(route: Route, value: unknown, status = 200, headers: Record<string, string> = {}) {
  return route.fulfill({
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(value),
  });
}
