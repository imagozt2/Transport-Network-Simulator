import { expect, test } from '@playwright/test';

const operator = {
  id: 1,
  username: 'admin',
  email: 'admin@macegocia.local',
  firstName: 'Iván',
  lastName: 'Administrador',
  role: 'ADMINISTRATOR',
  status: 'ACTIVE',
  lastLoginAt: '2026-08-15T10:00:00Z',
  createdAt: '2026-01-01T10:00:00Z'
};

test('completa el acceso y la navegación con una API simulada', async ({ page }) => {
  let submittedCredentials: unknown;

  await page.route('http://localhost:8080/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;

    if (path === '/api/auth/me') {
      await route.fulfill({ status: 401, json: { message: 'Unauthorized' } });
      return;
    }
    if (path === '/api/auth/csrf') {
      await route.fulfill({
        json: { headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'e2e-csrf-token' }
      });
      return;
    }
    if (path === '/api/auth/login') {
      submittedCredentials = request.postDataJSON();
      await route.fulfill({ json: operator });
      return;
    }
    if (path === '/api/operators/me/display-preferences') {
      await route.fulfill({ json: { timeZone: 'Europe/Madrid', theme: 'LIGHT' } });
      return;
    }

    const responses: Record<string, object> = {
      '/api/lines/operations': {
        evaluatedAt: '2026-08-15T10:00:00Z', phase: 'OPERATING', activeLineCount: 0, lines: []
      },
      '/api/stations/operations': {
        evaluatedAt: '2026-08-15T10:00:00Z', phase: 'OPERATING', stationCount: 0,
        activeStationCount: 0,
        summary: {
          stationCount: 0, activeStationCount: 0, transferStationCount: 0,
          ticketMachineCount: 0, entryValidatorCount: 0, exitValidatorCount: 0
        },
        stations: []
      },
      '/api/trains/operations': {
        evaluatedAt: '2026-08-15T10:00:00Z', phase: 'OPERATING',
        summary: {
          activeFleet: 0, trainsInService: 0, trainsInDepots: 0,
          byStatus: {}, byRole: {}, bySeries: {}
        },
        trains: []
      },
      '/api/depots/operations': {
        evaluatedAt: '2026-08-15T10:00:00Z', phase: 'OPERATING',
        summary: {
          depotCount: 0, totalCapacity: 0, occupiedSpaces: 0, availableSpaces: 0,
          occupancyPercentage: 0, assignedFleet: 0, trainsInService: 0,
          movements: {
            total: 0, exits: 0, entries: 0, completed: 0, scheduled: 0, nextMovementAt: null
          }
        },
        depots: []
      },
      '/api/devices/operations': {
        evaluatedAt: '2026-08-15T10:00:00Z',
        summary: {
          totalDevices: 0, filteredDevices: 0,
          byType: {}, byStatus: {}, byConnectivity: {}
        },
        devices: []
      }
    };
    await route.fulfill({ status: responses[path] ? 200 : 404, json: responses[path] ?? {} });
  });

  await page.goto('/dashboard');

  await expect(page).toHaveURL(/\/login(?:\?.*)?$/);
  await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();

  await page.getByLabel('Usuario o correo electrónico').fill('admin');
  await page.getByPlaceholder('Contraseña', { exact: true }).fill('contraseña-e2e-segura');
  await page.getByRole('button', { name: 'Acceder al centro de control' }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Panel general' })).toBeVisible();
  await expect(page.locator('#primary-navigation')).toBeVisible();
  await expect(page.getByText('Iván Administrador')).toBeVisible();
  expect(submittedCredentials).toEqual({
    identifier: 'admin',
    password: 'contraseña-e2e-segura'
  });
});
