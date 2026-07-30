import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { routes } from '../app.routes';
import { Depots } from './depots/depots';
import { Devices } from './devices/devices';
import { Logs } from './logs/logs';
import { Stations } from './stations/stations';
import { Trains } from './trains/trains';

interface ContextualNavigation {
  sourcePath: string;
  sourceComponent: unknown;
  destinationPath: string;
  destinationComponent: unknown;
  queryParam: 'stationCode' | 'deviceCode' | 'depotCode';
  value: string;
}

const contextualNavigations: readonly ContextualNavigation[] = [
  {
    sourcePath: 'stations',
    sourceComponent: Stations,
    destinationPath: 'devices',
    destinationComponent: Devices,
    queryParam: 'stationCode',
    value: 'ST001'
  },
  {
    sourcePath: 'stations',
    sourceComponent: Stations,
    destinationPath: 'logs',
    destinationComponent: Logs,
    queryParam: 'stationCode',
    value: 'ST001'
  },
  {
    sourcePath: 'devices',
    sourceComponent: Devices,
    destinationPath: 'logs',
    destinationComponent: Logs,
    queryParam: 'deviceCode',
    value: 'RMM-MB-ST001-001'
  },
  {
    sourcePath: 'depots',
    sourceComponent: Depots,
    destinationPath: 'trains',
    destinationComponent: Trains,
    queryParam: 'depotCode',
    value: 'DEP-AIR-A'
  }
];

describe('Contextual navigation between operational sections', () => {
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes)]
    });
    router = TestBed.inject(Router);
  });

  it.each(contextualNavigations)(
    'should preserve the context from $sourcePath to $destinationPath',
    (navigation) => {
      const sourceRoute = operationalRoute(navigation.sourcePath);
      const destinationRoute = operationalRoute(navigation.destinationPath);

      expect(sourceRoute?.component).toBe(navigation.sourceComponent);
      expect(destinationRoute?.component).toBe(navigation.destinationComponent);

      const url = router.serializeUrl(router.createUrlTree(
        [`/${navigation.destinationPath}`],
        { queryParams: { [navigation.queryParam]: navigation.value } }
      ));
      const parsedUrl = router.parseUrl(url);

      expect(url).toBe(
        `/${navigation.destinationPath}?${navigation.queryParam}=${navigation.value}`
      );
      expect(parsedUrl.root.children['primary'].segments.map((segment) => segment.path))
        .toEqual([navigation.destinationPath]);
      expect(parsedUrl.queryParams[navigation.queryParam]).toBe(navigation.value);
    }
  );
});

function operationalRoute(path: string) {
  return routes
    .find((route) => route.path === '')
    ?.children
    ?.find((route) => route.path === path);
}
