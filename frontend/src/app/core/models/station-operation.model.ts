import { ServiceDirection, ServiceOperationPhase } from './line-operation.model';

export type StationOperationStatus = 'NORMAL' | 'DEGRADED' | 'CRITICAL' | 'NO_TRAINS' | 'CLOSED';

export interface StationOperationsResponse {
  evaluatedAt: string;
  phase: ServiceOperationPhase;
  stationCount: number;
  activeStationCount: number;
  summary: StationOperationsSummary;
  stations: StationOperation[];
}

export interface StationOperationsSummary {
  stationCount: number;
  activeStationCount: number;
  transferStationCount: number;
  ticketMachineCount: number;
  entryValidatorCount: number;
  exitValidatorCount: number;
}

export interface StationOperation {
  id: number;
  code: string;
  name: string;
  status: StationOperationStatus;
  transferStation: boolean;
  lineCount: number;
  activeLineCount: number;
  activeTrainCount: number;
  devices: StationOperationDevices;
  lines: StationOperationLine[];
  nextArrivals: StationArrival[];
}

export interface StationOperationDevices {
  total: number;
  ticketMachines: number;
  entryValidators: number;
  exitValidators: number;
  online: number;
  offline: number;
  maintenance: number;
  errors: number;
}

export interface StationOperationTerminal {
  id: number;
  code: string;
  name: string;
}

export interface StationOperationLine {
  id: number;
  code: string;
  name: string;
  color: string;
  stationOrder: number;
  phase: ServiceOperationPhase;
  serviceOpen: boolean;
  activeTrainCount: number;
  firstTerminal: StationOperationTerminal;
  lastTerminal: StationOperationTerminal;
  directions: StationOperationDirection[];
}

export interface StationOperationDirection {
  direction: ServiceDirection;
  destination: StationOperationTerminal;
  activeTrainCount: number;
}

export interface StationArrival {
  trainId: number;
  trainCode: string;
  trainSeries: string;
  lineId: number;
  lineCode: string;
  lineName: string;
  lineColor: string;
  direction: ServiceDirection;
  destination: StationOperationTerminal;
  stationsAway: number;
  secondsUntilArrival: number;
  estimatedArrivalAt: string;
  atStation: boolean;
}
