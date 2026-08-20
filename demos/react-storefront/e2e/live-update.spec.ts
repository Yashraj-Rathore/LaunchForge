import { expect, test } from '@playwright/test';

test('two deterministic users update and a published kill switch reaches React without redeploy', async ({
  page,
  request,
}) => {
  await page.goto('/');

  await expect(page.getByTestId('checkout-experience')).toHaveText('Express checkout');
  await expect(page.getByTestId('reason')).toHaveText('RULE_MATCH');
  await expect(page.getByTestId('revision')).toHaveText('1');

  await page.getByRole('button', { name: 'Alex / US Free' }).click();
  await expect(page.getByTestId('checkout-experience')).toHaveText('Classic checkout');
  await expect(page.getByTestId('reason')).toHaveText('ROLLOUT_MATCH');
  await expect(page.getByTestId('bucket')).toHaveText('50000');

  await page.getByRole('button', { name: 'Ivy / Rollout cohort' }).click();
  await expect(page.getByTestId('checkout-experience')).toHaveText('Classic checkout');
  await expect(page.getByTestId('bucket')).toHaveText('29240');

  const rolloutResponse = await request.post('http://127.0.0.1:5174/demo/publish-rollout');
  expect(rolloutResponse.ok()).toBeTruthy();
  await expect(page.getByTestId('revision')).toHaveText('2');
  await expect(page.getByTestId('checkout-experience')).toHaveText('Express checkout');
  await expect(page.getByTestId('reason')).toHaveText('ROLLOUT_MATCH');

  await page.getByRole('button', { name: 'Guest / Missing key' }).click();
  await expect(page.getByTestId('checkout-experience')).toHaveText('Classic checkout');
  await expect(page.getByTestId('reason')).toHaveText('MISSING_ROLLOUT_KEY');

  await page.getByRole('button', { name: 'Maya / Canada Pro' }).click();
  await expect(page.getByTestId('checkout-experience')).toHaveText('Express checkout');

  const response = await request.post('http://127.0.0.1:5174/demo/publish-kill-switch');
  expect(response.ok()).toBeTruthy();
  await expect(page.getByTestId('revision')).toHaveText('3');
  await expect(page.getByTestId('checkout-experience')).toHaveText('Classic checkout');
  await expect(page.getByTestId('reason')).toHaveText('FLAG_DISABLED');
});
