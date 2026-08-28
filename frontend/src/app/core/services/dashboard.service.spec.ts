import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { DepotOperationsService } from './depot-operations.service';
import { DeviceOperationsService } from './device-operations.service';
import { DashboardService } from './dashboard.service';
import { LineOperationsService } from './line-operations.service';
import { StationOperationsService } from './station-operations.service';
import { TrainOperationsService } from './train-operations.service';

describe('DashboardService', () => {
  it('should build the dashboard from the live operational summaries', () => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: LineOperationsService,
          useValue: {
            getOperations: () => of({
              activeLineCount: 0,
              lines: [{
                id: 1,
                code: 'L1',
                name: 'Línea 1',
                color: 'Roja',
                serviceOpen: true,
                activeTrainCount: 14
              }]
            })
          }
        },
        {
          provide: StationOperationsService,
          useValue: { getOperations: () => of({ stationCount: 50 }) }
        },
        {
          provide: TrainOperationsService,
          useValue: {
            getOperations: () => of({
              summary: {
                activeFleet: 242,
                trainsInService: 84,
                byStatus: {
                  IN_SERVICE: 84,
                  DEPOT: 150,
                  MAINTENANCE: 4,
                  STOPPED: 2,
                  OUT_OF_SERVICE: 2
                }
              }
            })
          }
        },
        {
          provide: DepotOperationsService,
          useValue: {
            getOperations: () => of({
              summary: {
                depotCount: 6,
                occupancyPercentage: 65,
                totalCapacity: 300,
                occupiedSpaces: 195,
                availableSpaces: 105
              },
              depots: [{
                id: 1,
                code: 'CC',
                name: 'Cochera de Cuatro Caminos',
                capacity: 50,
                occupiedSpaces: 32,
                availableSpaces: 18
              }]
            })
          }
        },
        {
          provide: DeviceOperationsService,
          useValue: {
            getOperations: () => of({
              summary: {
                totalDevices: 622,
                byStatus: { ONLINE: 622, OFFLINE: 0, MAINTENANCE: 0, ERROR: 0 },
                byType: { TICKET_MACHINE: 126, ENTRY_VALIDATOR: 248, EXIT_VALIDATOR: 248 }
              }
            })
          }
        }
      ]
    });

    const service = TestBed.inject(DashboardService);

    service.getSummary().subscribe((summary) => {
      expect(summary).toEqual({
        lineCount: 1,
        stationCount: 50,
        totalFleet: 242,
        trainsInService: 84,
        deviceCount: 622,
        depotCount: 6,
        depotOccupancyPercentage: 65,
        trainStatusCounts: {
          IN_SERVICE: 84,
          DEPOT: 150,
          MAINTENANCE: 4,
          STOPPED: 2,
          OUT_OF_SERVICE: 2
        },
        deviceStatusCounts: { ONLINE: 622, OFFLINE: 0, MAINTENANCE: 0, ERROR: 0 },
        deviceTypeCounts: { TICKET_MACHINE: 126, ENTRY_VALIDATOR: 248, EXIT_VALIDATOR: 248 },
        depotCapacity: 300,
        occupiedDepotSpaces: 195,
        availableDepotSpaces: 105,
        depots: [{
          id: 1,
          code: 'CC',
          name: 'Cochera de Cuatro Caminos',
          capacity: 50,
          occupiedSpaces: 32,
          availableSpaces: 18
        }],
        lines: [{
          id: 1,
          code: 'L1',
          name: 'Línea 1',
          color: 'Roja',
          serviceOpen: true,
          activeTrainCount: 14
        }]
      });
    });
  });
});
