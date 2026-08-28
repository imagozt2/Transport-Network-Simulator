import { inject, Injectable } from '@angular/core';
import { forkJoin, map, Observable } from 'rxjs';
import { DashboardResponse } from '../models/dashboard.model';
import { DepotOperationsService } from './depot-operations.service';
import { DeviceOperationsService } from './device-operations.service';
import { LineOperationsService } from './line-operations.service';
import { StationOperationsService } from './station-operations.service';
import { TrainOperationsService } from './train-operations.service';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly lineOperationsService = inject(LineOperationsService);
  private readonly stationOperationsService = inject(StationOperationsService);
  private readonly trainOperationsService = inject(TrainOperationsService);
  private readonly depotOperationsService = inject(DepotOperationsService);
  private readonly deviceOperationsService = inject(DeviceOperationsService);

  getSummary(): Observable<DashboardResponse> {
    return forkJoin({
      lines: this.lineOperationsService.getOperations(),
      stations: this.stationOperationsService.getOperations(),
      trains: this.trainOperationsService.getOperations(),
      depots: this.depotOperationsService.getOperations(),
      devices: this.deviceOperationsService.getOperations()
    }).pipe(
      map(({ lines, stations, trains, depots, devices }) => ({
        lineCount: lines.lines.length,
        stationCount: stations.stationCount,
        totalFleet: trains.summary.activeFleet,
        trainsInService: trains.summary.trainsInService,
        deviceCount: devices.summary.totalDevices,
        depotCount: depots.summary.depotCount,
        depotOccupancyPercentage: depots.summary.occupancyPercentage,
        trainStatusCounts: trains.summary.byStatus,
        deviceStatusCounts: devices.summary.byStatus,
        deviceTypeCounts: devices.summary.byType,
        depotCapacity: depots.summary.totalCapacity,
        occupiedDepotSpaces: depots.summary.occupiedSpaces,
        availableDepotSpaces: depots.summary.availableSpaces,
        depots: depots.depots.map((depot) => ({
          id: depot.id,
          code: depot.code,
          name: depot.name,
          capacity: depot.capacity,
          occupiedSpaces: depot.occupiedSpaces,
          availableSpaces: depot.availableSpaces
        })),
        lines: lines.lines.map((line) => ({
          id: line.id,
          code: line.code,
          name: line.name,
          color: line.color,
          serviceOpen: line.serviceOpen,
          activeTrainCount: line.activeTrainCount
        }))
      }))
    );
  }
}
