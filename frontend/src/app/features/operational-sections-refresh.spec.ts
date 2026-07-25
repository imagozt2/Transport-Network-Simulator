import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';

import { DepotOperationsService } from '../core/services/depot-operations.service';
import { DeviceOperationsService } from '../core/services/device-operations.service';
import { LineOperationsService } from '../core/services/line-operations.service';
import { StationOperationsService } from '../core/services/station-operations.service';
import { TrainOperationsService } from '../core/services/train-operations.service';
import { Depots } from './depots/depots';
import { Devices } from './devices/devices';
import { Lines } from './lines/lines';
import { Stations } from './stations/stations';
import { Trains } from './trains/trains';

interface PeriodicallyRefreshedComponent {
  ngOnInit(): void;
  ngOnDestroy(): void;
  loadOperations(showLoading?: boolean): void;
}

const evaluatedAt = '2026-07-23T08:00:00+02:00';
const lineResponse = { evaluatedAt, phase: 'CLOSED', activeLineCount: 0, lines: [] };
const stationResponse = {
  evaluatedAt, phase: 'CLOSED', stationCount: 0, activeStationCount: 0,
  summary: {
    stationCount: 0, activeStationCount: 0, transferStationCount: 0,
    ticketMachineCount: 0, entryValidatorCount: 0, exitValidatorCount: 0
  },
  stations: []
};
const trainResponse = {
  evaluatedAt,
  phase: 'CLOSED',
  summary: {
    activeFleet: 0,
    trainsInService: 0,
    trainsInDepots: 0,
    byStatus: { IN_SERVICE: 0, DEPOT: 0, MAINTENANCE: 0, STOPPED: 0, OUT_OF_SERVICE: 0 },
    byRole: { REGULAR_SERVICE: 0, RESERVE: 0, HISTORIC: 0 },
    bySeries: {}
  },
  trains: []
};
const depotResponse = {
  evaluatedAt,
  phase: 'CLOSED',
  summary: {
    depotCount: 0,
    totalCapacity: 0,
    occupiedSpaces: 0,
    availableSpaces: 0,
    occupancyPercentage: 0,
    assignedFleet: 0,
    trainsInService: 0,
    movements: {
      total: 0, exits: 0, entries: 0, completed: 0, scheduled: 0, nextMovementAt: null
    }
  },
  depots: []
};
const deviceResponse = {
  evaluatedAt,
  summary: {
    totalDevices: 0,
    filteredDevices: 0,
    byType: { TICKET_MACHINE: 0, ENTRY_VALIDATOR: 0, EXIT_VALIDATOR: 0 },
    byStatus: { ONLINE: 0, OFFLINE: 0, MAINTENANCE: 0, ERROR: 0 }
  },
  devices: []
};

describe('Operational sections periodic refresh', () => {
  const lineService = { getOperations: vi.fn() };
  const stationService = { getOperations: vi.fn() };
  const trainService = { getOperations: vi.fn() };
  const depotService = { getOperations: vi.fn() };
  const deviceService = { getOperations: vi.fn() };

  beforeEach(async () => {
    vi.useFakeTimers();
    [
      lineService.getOperations,
      stationService.getOperations,
      trainService.getOperations,
      depotService.getOperations,
      deviceService.getOperations
    ].forEach((mock) => mock.mockReset());
    lineService.getOperations.mockReturnValue(of(lineResponse));
    stationService.getOperations.mockReturnValue(of(stationResponse));
    trainService.getOperations.mockReturnValue(of(trainResponse));
    depotService.getOperations.mockReturnValue(of(depotResponse));
    deviceService.getOperations.mockReturnValue(of(deviceResponse));

    await TestBed.configureTestingModule({
      imports: [Lines, Stations, Trains, Depots, Devices],
      providers: [
        provideRouter([]),
        { provide: LineOperationsService, useValue: lineService },
        { provide: StationOperationsService, useValue: stationService },
        { provide: TrainOperationsService, useValue: trainService },
        { provide: DepotOperationsService, useValue: depotService },
        { provide: DeviceOperationsService, useValue: deviceService }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should refresh every section at its configured interval and stop after destruction', () => {
    verifyRefreshLifecycle(Lines, 5_000);
    verifyRefreshLifecycle(Stations, 15_000);
    verifyRefreshLifecycle(Trains, 15_000);
    verifyRefreshLifecycle(Depots, 15_000);
    verifyRefreshLifecycle(Devices, 15_000);
  });

  it('should not overlap line requests when a previous refresh is still pending', () => {
    const pendingResponse = new Subject<typeof lineResponse>();
    lineService.getOperations.mockReturnValue(pendingResponse);
    const fixture = TestBed.createComponent(Lines);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    expect(lineService.getOperations).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(15_000);
    expect(lineService.getOperations).toHaveBeenCalledTimes(1);

    pendingResponse.next(lineResponse);
    pendingResponse.complete();
    vi.advanceTimersByTime(5_000);
    expect(lineService.getOperations).toHaveBeenCalledTimes(2);

    component.ngOnDestroy();
  });
});

function verifyRefreshLifecycle<T extends PeriodicallyRefreshedComponent>(
  componentType: Type<T>,
  intervalMs: number
): void {
  const fixture: ComponentFixture<T> = TestBed.createComponent(componentType);
  const component = fixture.componentInstance;
  fixture.detectChanges();
  const originalLoadOperations = component.loadOperations.bind(component);
  const loadOperations = vi.fn((showLoading?: boolean) =>
    originalLoadOperations(showLoading)
  );
  component.loadOperations = loadOperations;

  vi.advanceTimersByTime(intervalMs);
  expect(loadOperations).toHaveBeenCalledTimes(1);

  component.ngOnDestroy();
  vi.advanceTimersByTime(intervalMs * 2);
  expect(loadOperations).toHaveBeenCalledTimes(1);
}
