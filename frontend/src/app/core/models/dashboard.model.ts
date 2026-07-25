import { DeviceStatus, DeviceType } from './device-operation.model';
import { TrainStatus } from './train-operation.model';

export interface DashboardResponse {
  lineCount: number;
  stationCount: number;
  totalFleet: number;
  trainsInService: number;
  deviceCount: number;
  depotCount: number;
  depotOccupancyPercentage: number;
  trainStatusCounts: Record<TrainStatus, number>;
  deviceStatusCounts: Record<DeviceStatus, number>;
  deviceTypeCounts: Record<DeviceType, number>;
  depotCapacity: number;
  occupiedDepotSpaces: number;
  availableDepotSpaces: number;
  depots: DashboardDepot[];
  lines: DashboardLine[];
}

export interface DashboardDepot {
  id: number;
  code: string;
  name: string;
  capacity: number;
  occupiedSpaces: number;
  availableSpaces: number;
}

export interface DashboardLine {
  id: number;
  code: string;
  name: string;
  color: string;
  serviceOpen: boolean;
  activeTrainCount: number;
}
