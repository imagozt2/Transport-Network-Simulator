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
  stations: LineOperationStation[];
  trains: LineOperationTrain[];
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
