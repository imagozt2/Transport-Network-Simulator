import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { routes } from '../app.routes';
import { Dashboard } from './dashboard/dashboard';
import { Depots } from './depots/depots';
import { Devices } from './devices/devices';
import { Lines } from './lines/lines';
import { Logs } from './logs/logs';
import { Stations } from './stations/stations';
import { Trains } from './trains/trains';

interface ContextualNavigation {
  sourcePath: string;
  sourceComponent: unknown;
  destinationPath: string;
  destinationComponent: unknown;
  queryParams: Record<string, string>;
  expectedUrl: string;
}

const contextualNavigations: readonly ContextualNavigation[] = [
  {
    sourcePath: 'dashboard', sourceComponent: Dashboard,
    destinationPath: 'trains', destinationComponent: Trains,
    queryParams: {}, expectedUrl: '/trains'
  },
  {
    sourcePath: 'dashboard', sourceComponent: Dashboard,
    destinationPath: 'devices', destinationComponent: Devices,
    queryParams: {}, expectedUrl: '/devices'
  },
  {
    sourcePath: 'dashboard', sourceComponent: Dashboard,
    destinationPath: 'depots', destinationComponent: Depots,
    queryParams: {}, expectedUrl: '/depots'
  },
  {
    sourcePath: 'dashboard', sourceComponent: Dashboard,
    destinationPath: 'lines', destinationComponent: Lines,
    queryParams: {}, expectedUrl: '/lines'
  },
  {
    sourcePath: 'lines', sourceComponent: Lines,
    destinationPath: 'trains', destinationComponent: Trains,
    queryParams: { lineCode: 'L3' }, expectedUrl: '/trains?lineCode=L3'
  },
  {
    sourcePath: 'lines', sourceComponent: Lines,
    destinationPath: 'depots', destinationComponent: Depots,
    queryParams: { lineCode: 'L3' }, expectedUrl: '/depots?lineCode=L3'
  },
  {
    sourcePath: 'lines', sourceComponent: Lines,
    destinationPath: 'stations', destinationComponent: Stations,
    queryParams: { lineCode: 'L3' }, expectedUrl: '/stations?lineCode=L3'
  },
  {
    sourcePath: 'lines', sourceComponent: Lines,
    destinationPath: 'trains', destinationComponent: Trains,
    queryParams: { trainCode: 'T-9001' }, expectedUrl: '/trains?trainCode=T-9001'
  },
  {
    sourcePath: 'lines', sourceComponent: Lines,
    destinationPath: 'stations', destinationComponent: Stations,
    queryParams: { stationCode: 'ST001' }, expectedUrl: '/stations?stationCode=ST001'
  },
  {
    sourcePath: 'stations', sourceComponent: Stations,
    destinationPath: 'trains', destinationComponent: Trains,
    queryParams: { lineCode: 'L3', status: 'IN_SERVICE' },
    expectedUrl: '/trains?lineCode=L3&status=IN_SERVICE'
  },
  {
    sourcePath: 'stations',
    sourceComponent: Stations,
    destinationPath: 'devices',
    destinationComponent: Devices,
    queryParams: { stationCode: 'ST001' },
    expectedUrl: '/devices?stationCode=ST001'
  },
  {
    sourcePath: 'stations',
    sourceComponent: Stations,
    destinationPath: 'logs',
    destinationComponent: Logs,
    queryParams: { stationCode: 'ST001' },
    expectedUrl: '/logs?stationCode=ST001'
  },
  {
    sourcePath: 'devices',
    sourceComponent: Devices,
    destinationPath: 'logs',
    destinationComponent: Logs,
    queryParams: { deviceCode: 'RMM-MB-ST001-001' },
    expectedUrl: '/logs?deviceCode=RMM-MB-ST001-001'
  },
  {
    sourcePath: 'depots',
    sourceComponent: Depots,
    destinationPath: 'trains',
    destinationComponent: Trains,
    queryParams: { depotCode: 'DEP-AIR-A' },
    expectedUrl: '/trains?depotCode=DEP-AIR-A'
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
        { queryParams: navigation.queryParams }
      ));
      const parsedUrl = router.parseUrl(url);

      expect(url).toBe(navigation.expectedUrl);
      expect(parsedUrl.root.children['primary'].segments.map((segment) => segment.path))
        .toEqual([navigation.destinationPath]);
      expect(parsedUrl.queryParams).toEqual(navigation.queryParams);
    }
  );

  it('should only send filters supported by each destination section', () => {
    const supportedFilters: Readonly<Record<string, readonly string[]>> = {
      trains: ['lineCode', 'status', 'trainCode', 'depotCode'],
      depots: ['lineCode'],
      stations: ['lineCode', 'stationCode'],
      devices: ['stationCode'],
      logs: ['stationCode', 'deviceCode'],
      dashboard: [],
      lines: []
    };

    for (const navigation of contextualNavigations) {
      expect(Object.keys(navigation.queryParams).every((parameter) =>
        supportedFilters[navigation.destinationPath]?.includes(parameter)
      )).toBe(true);
    }
  });

  it('should keep contextual identifiers normalized and compatible between sections', () => {
    for (const navigation of contextualNavigations) {
      const { lineCode, stationCode, trainCode, depotCode, status } = navigation.queryParams;
      if (lineCode) expect(lineCode).toMatch(/^L\d+$/);
      if (stationCode) expect(stationCode).toMatch(/^ST\d{3}$/);
      if (trainCode) expect(trainCode).toMatch(/^[A-Z0-9-]+$/);
      if (depotCode) expect(depotCode).toMatch(/^DEP-[A-Z0-9-]+$/);
      if (status) expect(status).toBe('IN_SERVICE');
    }
  });
});

function operationalRoute(path: string) {
  return routes
    .find((route) => route.path === '')
    ?.children
    ?.find((route) => route.path === path);
}
