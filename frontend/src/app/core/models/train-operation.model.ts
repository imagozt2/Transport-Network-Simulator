import { ServiceDirection, ServiceOperationPhase, TrainPositionState } from './line-operation.model';

export type FleetRole = 'REGULAR_SERVICE' | 'RESERVE' | 'HISTORIC';
export type TrainStatus = 'IN_SERVICE' | 'DEPOT' | 'MAINTENANCE' | 'STOPPED' | 'OUT_OF_SERVICE';

export interface TrainOperationsResponse {
  evaluatedAt: string;
  phase: ServiceOperationPhase;
  summary: TrainFleetSummary;
  trains: TrainOperation[];
}

export interface TrainFleetSummary {
  activeFleet: number;
  trainsInService: number;
  trainsInDepots: number;
  byStatus: Record<TrainStatus, number>;
  byRole: Record<FleetRole, number>;
  bySeries: Record<string, number>;
}

export interface TrainOperation {
  id: number;
  code: string;
  manufacturer: string;
  model: string;
  series: string;
  carCount: number;
  passengerCapacity: number;
  maximumSpeedKmh: number;
  fleetRole: FleetRole;
  status: TrainStatus;
  dispatchOrder: number | null;
  assignedLine: TrainOperationLine;
  homeDepot: TrainOperationDepot;
  currentDepot: TrainOperationDepot | null;
  serviceLocation: TrainServiceLocation | null;
}

export interface TrainOperationLine {
  id: number;
  code: string;
  name: string;
  color: string;
}

export interface TrainOperationDepot {
  id: number;
  code: string;
  name: string;
  stationId: number;
  stationCode: string;
  stationName: string;
}

export interface TrainOperationStation {
  id: number;
  code: string;
  name: string;
}

export interface TrainServiceLocation {
  currentLine: TrainOperationLine;
  dutyNumber: number;
  positionState: TrainPositionState;
  direction: ServiceDirection;
  destination: TrainOperationStation;
  currentStation: TrainOperationStation | null;
  previousStation: TrainOperationStation;
  nextStation: TrainOperationStation;
  progressPercentage: number;
  secondsUntilNextStation: number;
  estimatedArrivalAt: string;
}
