export type ServiceOperationPhase = 'CLOSED' | 'STARTING' | 'OPERATING' | 'ENDING';
export type ServicePeriodType = 'SERVICE_START' | 'OFF_PEAK' | 'PEAK' | 'REGULAR' | 'SERVICE_END';
export type ServiceDirection = 'OUTBOUND' | 'INBOUND';
export type TrainPositionState = 'AT_STATION' | 'BETWEEN_STATIONS';

export interface LineOperationsResponse {
  evaluatedAt: string;
  phase: ServiceOperationPhase;
  activeLineCount: number;
  lines: LineOperation[];
}

export interface LineOperation {
  id: number;
  code: string;
  name: string;
  color: string;
  phase: ServiceOperationPhase;
  serviceOpen: boolean;
  serviceStartsAt: string | null;
  serviceEndsAt: string | null;
  currentPeriodCode: string | null;
  currentPeriodType: ServicePeriodType | null;
  headwaySeconds: number | null;
  estimatedOneWayDurationSeconds: number | null;
  stationCount: number;
  firstTerminal: LineOperationStation;
  lastTerminal: LineOperationStation;
  activeTrainCount: number;
  depots: LineOperationDepot[];
  nextArrivals: LineOperationArrival[];
  stations: LineOperationStation[];
  trains: LineOperationTrain[];
}

export interface LineOperationDepot {
  id: number;
  code: string;
  name: string;
  station: LineOperationLocation;
  dispatchTerminal: LineOperationLocation;
  dispatchPriority: number;
  dispatchEnabled: boolean;
  receptionEnabled: boolean;
  assignedTrainCount: number;
  trainsInService: number;
  availableTrainCount: number;
}

export interface LineOperationArrival {
  stationId: number;
  stationCode: string;
  stationName: string;
  trainId: number;
  trainCode: string;
  trainSeries: string;
  direction: ServiceDirection;
  destinationStationId: number;
  destinationStationCode: string;
  destinationStationName: string;
  stationsAway: number;
  secondsUntilArrival: number;
  estimatedArrivalAt: string;
  atStation: boolean;
}

export interface LineOperationLocation {
  id: number;
  code: string;
  name: string;
}

export interface LineOperationStation {
  id: number;
  code: string;
  name: string;
  stationOrder: number;
}

export interface LineOperationTrain {
  id: number;
  code: string;
  series: string;
  dutyNumber: number;
  positionState: TrainPositionState;
  direction: ServiceDirection;
  currentStationId: number | null;
  currentStationCode: string | null;
  previousStationId: number;
  previousStationCode: string;
  nextStationId: number;
  nextStationCode: string;
  progressPercentage: number;
  secondsUntilNextStation: number;
  estimatedArrivalAt: string;
}
