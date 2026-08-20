import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { expect, test, type Page } from '@playwright/test';

test.skip(
  process.env.LAUNCHFORGE_DEMO_CAPTURE !== 'true',
  'Real demo capture runs only against the isolated deterministic Compose stack.',
);

const adminUrl = process.env.LAUNCHFORGE_E2E_BASE_URL ?? 'http://127.0.0.1:8080';
const storefrontUrl = process.env.LAUNCHFORGE_DEMO_STOREFRONT_URL ?? 'http://127.0.0.1:5174';
const mediaDirectory = path.resolve(process.cwd(), '../../demos/demo-media');
const requestedDelay = Number(process.env.LAUNCHFORGE_DEMO_STEP_DELAY_MS ?? '4000');
const stepDelayMs = Math.max(1_000, Math.min(5_000, requestedDelay));

async function deliberatePause(page: Page): Promise<void> {
  await page.waitForTimeout(stepDelayMs);
}

async function openFlag(page: Page, draftVersion: number): Promise<void> {
  await page.goto(adminUrl);
  await expect(page.getByRole('heading', { name: 'Feature flags' })).toBeVisible();
  await page.getByRole('heading', { name: 'New checkout' }).click();
  await expect(page.getByText(`Unpublished draft v${draftVersion}`)).toBeVisible();
}

async function publish(page: Page, reason: string, revision: number): Promise<void> {
  await page.getByRole('button', { name: 'Review and publish' }).click();
  await expect(page.getByRole('heading', { name: 'Publish to Development' })).toBeVisible();
  await page.getByLabel('Change reason / ticket reference').fill(reason);
  await deliberatePause(page);
  await page.getByRole('button', { name: 'Publish immutable revision' }).click();
  await expect(page.getByText(`Revision ${revision} is durable`)).toBeVisible();
  await deliberatePause(page);
  await page.getByRole('button', { name: 'Close publish review' }).click();
}

test('captures the deliberate real-system recruiter walkthrough', async ({ page, context }) => {
  test.setTimeout(420_000);
  page.setDefaultTimeout(20_000);
  const password = process.env.LAUNCHFORGE_E2E_PASSWORD;
  if (!password) throw new Error('LAUNCHFORGE_E2E_PASSWORD is required');
  await mkdir(mediaDirectory, { recursive: true });

  await page.goto(adminUrl);
  await page.getByRole('link', { name: 'Sign in with OpenID Connect' }).click();
  await page.locator('#username').fill('owner');
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await expect(page.getByRole('heading', { name: 'Feature flags' })).toBeVisible();
  await deliberatePause(page);
  await page.screenshot({
    path: path.join(mediaDirectory, '01-flag-workspace.png'),
    fullPage: true,
  });

  await page.getByRole('heading', { name: 'New checkout' }).click();
  await expect(page.getByText('Unpublished draft v0')).toBeVisible();
  await page.locator('#targeting-rules').scrollIntoViewIfNeeded();
  await deliberatePause(page);
  await page.screenshot({
    path: path.join(mediaDirectory, '02-targeting-and-rollout.png'),
    fullPage: true,
  });

  await page.locator('#draft-simulator').scrollIntoViewIfNeeded();
  await page.getByLabel('Context key').fill('internal-tester');
  await page
    .getByLabel('Scalar attributes (JSON object)')
    .fill('{"country":"US","plan":"internal","userId":"demo-26"}');
  await page.getByRole('button', { name: 'Run deterministic evaluation' }).click();
  await expect(page.getByText('29240')).toBeVisible();
  await deliberatePause(page);
  await page.screenshot({
    path: path.join(mediaDirectory, '03-deterministic-simulator.png'),
    fullPage: true,
  });

  const storefront = await context.newPage();
  await storefront.goto(storefrontUrl);
  await expect(storefront.getByTestId('revision')).toHaveText('1');
  await expect(storefront.getByTestId('checkout-experience')).toHaveText('Express checkout');
  await deliberatePause(storefront);
  await storefront.getByRole('button', { name: 'Ivy / Rollout cohort' }).click();
  await expect(storefront.getByTestId('checkout-experience')).toHaveText('Classic checkout');
  await expect(storefront.getByTestId('bucket')).toHaveText('29240');
  await deliberatePause(storefront);
  await storefront.screenshot({
    path: path.join(mediaDirectory, '04-storefront-ten-percent.png'),
    fullPage: true,
  });

  await page.bringToFront();
  await openFlag(page, 0);
  const weights = page.locator('#percentage-rollout input[type="number"]');
  await weights.nth(0).fill('50000');
  await weights.nth(1).fill('50000');
  await deliberatePause(page);
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect(page.getByText('Unpublished draft v1')).toBeVisible();
  await publish(page, 'Demo: expand stable cohort from 10% to 50%', 2);

  await storefront.bringToFront();
  await expect(storefront.getByTestId('revision')).toHaveText('2', { timeout: 30_000 });
  await expect(storefront.getByTestId('checkout-experience')).toHaveText('Express checkout');
  await deliberatePause(storefront);
  await storefront.screenshot({
    path: path.join(mediaDirectory, '05-live-rollout-update.png'),
    fullPage: true,
  });

  await page.bringToFront();
  await openFlag(page, 1);
  await page.getByLabel('Flag enabled').uncheck();
  await deliberatePause(page);
  await page.getByRole('button', { name: 'Save draft' }).click();
  await expect(page.getByText('Unpublished draft v2')).toBeVisible();
  await publish(page, 'Demo: activate the safe checkout kill switch', 3);

  await storefront.bringToFront();
  await expect(storefront.getByTestId('revision')).toHaveText('3', { timeout: 30_000 });
  await expect(storefront.getByTestId('checkout-experience')).toHaveText('Classic checkout');
  await expect(storefront.getByTestId('reason')).toHaveText('FLAG_DISABLED');
  await deliberatePause(storefront);
  await storefront.screenshot({
    path: path.join(mediaDirectory, '06-kill-switch.png'),
    fullPage: true,
  });

  await page.bringToFront();
  await page.getByRole('link', { name: 'Revisions' }).click();
  await expect(
    page.locator('#console-content').getByText('Published revision 3', { exact: true }),
  ).toBeVisible();
  await deliberatePause(page);
  await page.screenshot({
    path: path.join(mediaDirectory, '07-immutable-revisions.png'),
    fullPage: true,
  });
  await page.getByRole('link', { name: 'Audit' }).click();
  await expect(page.getByText('ENVIRONMENT_PUBLISHED').first()).toBeVisible();
  await deliberatePause(page);
  await page.screenshot({ path: path.join(mediaDirectory, '08-audit-trail.png'), fullPage: true });

  const adminVideo = page.video();
  const storefrontVideo = storefront.video();
  await page.close();
  await storefront.close();
  await adminVideo?.saveAs(path.join(mediaDirectory, 'launchforge-admin-tour.webm'));
  await storefrontVideo?.saveAs(path.join(mediaDirectory, 'northstar-live-update.webm'));
});
