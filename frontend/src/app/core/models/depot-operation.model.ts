import { ServiceOperationPhase } from './line-operation.model';
import { FleetRole, TrainStatus } from './train-operation.model';

export type DepotOperationStatus = 'EMPTY' | 'AVAILABLE' | 'HIGH_OCCUPANCY' | 'FULL' | 'OVER_CAPACITY';
export type DepotMovementType = 'EXIT' | 'ENTRY';
export type DepotMovementStatus = 'COMPLETED' | 'SCHEDULED';

export interface DepotOperationsResponse {
  evaluatedAt: string;
  phase: ServiceOperationPhase;
  summary: DepotOperationsSummary;
  depots: DepotOperation[];
}

export interface DepotOperationsSummary {
  depotCount: number;
  totalCapacity: number;
  occupiedSpaces: number;
  availableSpaces: number;
  occupancyPercentage: number;
  assignedFleet: number;
  trainsInService: number;
  movements: DepotMovementsSummary;
}

export interface DepotOperation {
  id: number;
  code: string;
  name: string;
  station: DepotOperationStation;
  capacity: number;
  trackCount: number;
  trainsPerTrack: number;
  occupiedSpaces: number;
  availableSpaces: number;
  occupancyPercentage: number;
  status: DepotOperationStatus;
  fleet: DepotFleetDistribution;
  movementsSummary: DepotMovementsSummary;
  movements: DepotMovement[];
}

export interface DepotOperationStation { id: number; code: string; name: string; }

export interface DepotFleetDistribution {
  assignedTrainCount: number;
  assignedTrainsInService: number;
  byStatus: Record<TrainStatus, number>;
  byRole: Record<FleetRole, number>;
  bySeries: Record<string, number>;
}

export interface DepotMovementsSummary {
  total: number;
  exits: number;
  entries: number;
  completed: number;
  scheduled: number;
  nextMovementAt: string | null;
}

export interface DepotMovement {
  dutyNumber: number;
  type: DepotMovementType;
  status: DepotMovementStatus;
  scheduledAt: string;
  secondsUntilMovement: number | null;
  train: { id: number; code: string; series: string; fleetRole: FleetRole };
  line: { id: number; code: string; name: string; color: string };
  terminal: DepotOperationStation;
}
