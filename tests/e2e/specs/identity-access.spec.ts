import { expect, test } from '@playwright/test';

test.skip(
  process.env.LAUNCHFORGE_RELEASE_SMOKE === 'true',
  'The release smoke uses environment-provided staging resources.',
);

test('OIDC login scopes the shell and logout invalidates the session', async ({ page }) => {
  const username = process.env.LAUNCHFORGE_E2E_USERNAME ?? 'owner';
  const password = process.env.LAUNCHFORGE_E2E_PASSWORD;
  if (!password) {
    throw new Error('LAUNCHFORGE_E2E_PASSWORD is required');
  }

  await page.goto('/');
  await page.getByRole('link', { name: 'Sign in with OpenID Connect' }).click();
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();

  await expect(page.getByRole('heading', { name: 'Feature flags' })).toBeVisible();
  await expect(page.getByText('Northstar Commerce')).toBeVisible();
  await expect(page.getByText('OWNER', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Project context')).toHaveValue(
    '40000000-0000-0000-0000-000000000001',
  );
  await expect(page.getByLabel('Environment context')).toHaveValue(
    '50000000-0000-0000-0000-000000000001',
  );

  const sessionCookie = (await page.context().cookies()).find(
    (cookie) => cookie.name === 'launchforge_session',
  );
  expect(sessionCookie?.httpOnly).toBe(true);
  expect(sessionCookie?.sameSite).toBe('Lax');
  expect(
    await page.evaluate(() => ({
      local: Object.keys(window.localStorage),
      session: Object.keys(window.sessionStorage),
    })),
  ).toEqual({ local: [], session: [] });

  const crossTenantStatus = await page.evaluate(async () => {
    const response = await fetch('/api/v1/organizations/90000000-0000-0000-0000-000000000009');
    return response.status;
  });
  expect(crossTenantStatus).toBe(404);

  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('link', { name: 'Sign in with OpenID Connect' })).toBeVisible();

  const sessionStatus = await page.evaluate(async () => {
    const response = await fetch('/api/v1/auth/me');
    return response.status;
  });
  expect(sessionStatus).toBe(401);
});
