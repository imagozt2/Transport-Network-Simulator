import { routes } from './app.routes';
import { MainLayout } from './layout/main-layout/main-layout';

describe('Application routes', () => {
  it('should place every operational section under the main layout', () => {
    const shellRoute = routes.find((route) => route.path === '');

    expect(shellRoute?.component).toBe(MainLayout);
    expect(shellRoute?.children?.map((route) => route.path)).toEqual([
      '',
      'dashboard',
      'network-map',
      'lines',
      'stations',
      'trains',
      'depots',
      'devices',
      'logs'
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
});
