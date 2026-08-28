import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { APPLICATION_ROUTES } from '../navigation/application-routes';
import { ActiveSectionService } from './active-section.service';

const directRoutes = Object.values(APPLICATION_ROUTES);
const contextualRoutes = [
  { url: '/lines?lineCode=L3', activeRoute: APPLICATION_ROUTES.lines },
  { url: '/stations?lineCode=L3&stationCode=ST001', activeRoute: APPLICATION_ROUTES.stations },
  { url: '/trains?lineCode=L3&status=IN_SERVICE', activeRoute: APPLICATION_ROUTES.trains },
  { url: '/depots?lineCode=L3', activeRoute: APPLICATION_ROUTES.depots },
  { url: '/devices?stationCode=ST001', activeRoute: APPLICATION_ROUTES.devices },
  { url: '/logs?deviceCode=RMM-MB-ST001-001', activeRoute: APPLICATION_ROUTES.logs },
  { url: '/incidents?deviceCode=RMM-MB-ST001-001', activeRoute: APPLICATION_ROUTES.incidents }
] as const;

describe('ActiveSectionService', () => {
  let router: Router;
  let service: ActiveSectionService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'dashboard', children: [] },
          { path: 'network-map', children: [] },
          { path: 'lines', children: [] },
          { path: 'stations', children: [] },
          { path: 'trains', children: [] },
          { path: 'depots', children: [] },
          { path: 'devices', children: [] },
          { path: 'transport-titles', children: [] },
          { path: 'users', children: [] },
          { path: 'incidents', children: [] },
          { path: 'logs', children: [] },
          { path: 'account', children: [] },
          { path: 'settings', children: [] }
        ])
      ]
    });

    router = TestBed.inject(Router);
    service = TestBed.inject(ActiveSectionService);
  });

  it.each(directRoutes)('should detect a direct navigation to %s', async (route) => {
    await router.navigateByUrl(route);

    expect(service.activeSection()).toBe(route.slice(1));
    expect(service.isActive(route)).toBe(true);
  });

  it.each(contextualRoutes)(
    'should preserve $activeRoute as active for $url',
    async ({ url, activeRoute }) => {
      await router.navigateByUrl(url);

      expect(service.activeSection()).toBe(activeRoute.slice(1));
      expect(service.isActive(activeRoute)).toBe(true);
    }
  );

  it('should replace the active section after a contextual navigation', async () => {
    await router.navigateByUrl('/stations?lineCode=L2');
    expect(service.isActive(APPLICATION_ROUTES.stations)).toBe(true);

    await router.navigateByUrl('/trains?lineCode=L2&status=IN_SERVICE');

    expect(service.activeSection()).toBe('trains');
    expect(service.isActive(APPLICATION_ROUTES.trains)).toBe(true);
    expect(service.isActive(APPLICATION_ROUTES.stations)).toBe(false);
  });
});
