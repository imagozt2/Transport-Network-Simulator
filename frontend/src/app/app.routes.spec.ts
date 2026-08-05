import { routes } from './app.routes';
import {
  guestOperatorGuard,
  operatorAuthGuard
} from './core/guards/operator-auth.guard';
import { MainLayout } from './layout/main-layout/main-layout';

describe('Application routes', () => {
  it('should place every operational section under the main layout', () => {
    const shellRoute = routes.find((route) => route.path === '');

    expect(shellRoute?.component).toBe(MainLayout);
    expect(shellRoute?.children?.map((route) => route.path)).toEqual([
      '',
      'dashboard',
      'account',
      'settings',
      'network-map',
      'lines',
      'stations',
      'trains',
      'depots',
      'transport-titles',
      'users',
      'devices',
      'logs',
      'incidents'
    ]);
  });

  it('should redirect the root and unknown routes to the general dashboard', () => {
    const shellRoute = routes.find((route) => route.path === '');
    const rootRedirect = shellRoute?.children?.find((route) => route.path === '');
    const wildcardRedirect = routes.find((route) => route.path === '**');

    expect(rootRedirect).toEqual(expect.objectContaining({
      pathMatch: 'full',
      redirectTo: 'dashboard'
    }));
    expect(wildcardRedirect?.redirectTo).toBe('dashboard');
  });

  it('should keep login public and protect the complete application shell', () => {
    const loginRoute = routes.find((route) => route.path === 'login');
    const shellRoute = routes.find((route) => route.path === '');

    expect(loginRoute?.canActivate).toEqual([guestOperatorGuard]);
    expect(shellRoute?.canActivate).toEqual([operatorAuthGuard]);
    expect(shellRoute?.children?.every((route) => route.canActivate === undefined)).toBe(true);
  });
});
