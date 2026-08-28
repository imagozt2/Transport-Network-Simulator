import { expect, test } from '@playwright/test';

test.skip(
  process.env['RMM_E2E_REAL_BACKEND'] !== 'true',
  'Requiere el ecosistema temporal con backend y MySQL reales'
);

test('autentica al operador y carga el panel desde el backend real', async ({ page }) => {
  const username = process.env['RMM_E2E_OPERATOR_USERNAME'];
  const password = process.env['RMM_E2E_OPERATOR_PASSWORD'];

  expect(username).toBeTruthy();
  expect(password).toBeTruthy();

  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/login(?:\?.*)?$/);

  await page.getByLabel('Usuario o correo electrónico').fill(username!);
  await page.getByPlaceholder('Contraseña', { exact: true }).fill(password!);

  const loginResponsePromise = page.waitForResponse(
    (response) => response.url() === 'http://localhost:8080/api/auth/login'
  );
  const linesResponsePromise = page.waitForResponse(
    (response) => response.url() === 'http://localhost:8080/api/lines/operations'
  );

  await page.getByRole('button', { name: 'Acceder al centro de control' }).click();

  const loginResponse = await loginResponsePromise;
  expect(loginResponse.status()).toBe(200);

  const linesResponse = await linesResponsePromise;
  expect(linesResponse.status()).toBe(200);
  const operations = await linesResponse.json() as { lines: unknown[] };
  expect(operations.lines.length).toBeGreaterThan(0);

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Panel general' })).toBeVisible();
  await expect(page.locator('#primary-navigation')).toBeVisible();

  const networkLinesCard = page.locator('.summary-card').filter({ hasText: 'Líneas de la red' });
  await expect(networkLinesCard.locator('strong')).toHaveText(String(operations.lines.length));
  await expect(page.getByText('Container Administrator')).toBeVisible();
});
