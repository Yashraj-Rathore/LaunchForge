import { render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { ApiError } from './api';
import { App, MutationError, queryClient } from './App';

const organizationId = '10000000-0000-0000-0000-000000000001';
const projectId = '20000000-0000-0000-0000-000000000002';
const environmentId = '30000000-0000-0000-0000-000000000003';

afterEach(() => {
  queryClient.clear();
  window.history.replaceState({}, '', '/');
  vi.restoreAllMocks();
});

test('offers OIDC login when there is no server session', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 401 }));

  render(<App />);

  expect(await screen.findByRole('link', { name: /sign in with openid connect/i })).toHaveAttribute(
    'href',
    '/oauth2/authorization/keycloak',
  );
});

test('renders the server-authorized workspace context and production warning', async () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const path = String(input);
    if (path.endsWith('/auth/me')) {
      return json({
        subject: 'fictional-subject',
        displayName: 'owner',
        organizations: [
          {
            id: organizationId,
            slug: 'northstar-commerce',
            name: 'Northstar Commerce',
            role: 'OWNER',
          },
        ],
      });
    }
    if (path.endsWith(`/organizations/${organizationId}/projects`)) {
      return json([
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
    if (path.endsWith(`/projects/${projectId}/environments`)) {
      return json([
        {
          id: environmentId,
          projectId,
          key: 'production',
          name: 'Production',
          kind: 'PRODUCTION',
          status: 'ACTIVE',
          currentRevision: 7,
          version: 8,
        },
      ]);
    }
    if (path.endsWith(`/projects/${projectId}/flags`)) return json([]);
    return new Response(null, { status: 404 });
  });

  render(<App />);

  expect(await screen.findByRole('heading', { name: 'Feature flags' })).toBeInTheDocument();
  expect(screen.getByText('Northstar Commerce')).toBeInTheDocument();
  expect(screen.getByText('OWNER')).toBeInTheDocument();
  expect(screen.getByText('Production environment')).toBeInTheDocument();
  expect(screen.getByText('Published revision 7')).toBeInTheDocument();
});

test('labels analytics as optional when its isolated store is unavailable', async () => {
  window.history.replaceState(
    {},
    '',
    `/organizations/${organizationId}/projects/${projectId}/environments/${environmentId}/analytics`,
  );
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const path = String(input);
    if (path.endsWith('/auth/me')) {
      return json({
        subject: 'fictional-subject',
        displayName: 'owner',
        organizations: [
          {
            id: organizationId,
            slug: 'northstar-commerce',
            name: 'Northstar Commerce',
            role: 'OWNER',
          },
        ],
      });
    }
    if (path.endsWith(`/organizations/${organizationId}/projects`)) {
      return json([
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
    if (path.endsWith(`/projects/${projectId}/environments`)) {
      return json([
        {
          id: environmentId,
          projectId,
          key: 'production',
          name: 'Production',
          kind: 'PRODUCTION',
          status: 'ACTIVE',
          currentRevision: 7,
          version: 8,
        },
      ]);
    }
    if (path.includes(`/environments/${environmentId}/analytics/evaluations?`)) {
      return new Response(JSON.stringify({ code: 'ANALYTICS_UNAVAILABLE' }), {
        status: 503,
        headers: { 'Content-Type': 'application/problem+json' },
      });
    }
    return new Response(null, { status: 404 });
  });

  render(<App />);

  expect(await screen.findByRole('heading', { name: 'Evaluation analytics' })).toBeInTheDocument();
  expect(await screen.findByText('Analytics unavailable')).toBeInTheDocument();
  expect(
    screen.getByText(/configuration delivery, publish, and rollback are unaffected/u),
  ).toBeInTheDocument();
  expect(screen.getByText(/does not measure experiment significance/u)).toBeInTheDocument();
});

test('gives stale writes a safe reconciliation message', () => {
  render(
    <MutationError
      error={new ApiError(412, { code: 'STALE_RESOURCE_VERSION', detail: 'Version mismatch' })}
    />,
  );

  expect(screen.getByRole('alert')).toHaveTextContent(/local edits are preserved/u);
});

function json(value: unknown): Response {
  return new Response(JSON.stringify(value), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}
