import { render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { App } from './App';

afterEach(() => {
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

test('displays the server-authorized organization and role', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(
      JSON.stringify({
        subject: 'fictional-subject',
        displayName: 'owner',
        organizations: [
          {
            id: '10000000-0000-0000-0000-000000000001',
            slug: 'northstar-commerce',
            name: 'Northstar Commerce',
            role: 'OWNER',
          },
        ],
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ),
  );

  render(<App />);

  expect(await screen.findByRole('heading', { name: /welcome, owner/i })).toBeInTheDocument();
  expect(screen.getByText('Northstar Commerce')).toBeInTheDocument();
  expect(screen.getByText('OWNER')).toBeInTheDocument();
});
