import {
  DeviceEventSource,
  DeviceEventType,
  LogOrigin,
  LogSeverity
} from './operational-log.types';

export type DeviceType = 'TICKET_MACHINE' | 'ENTRY_VALIDATOR' | 'EXIT_VALIDATOR';
export type DeviceStatus = 'ONLINE' | 'OFFLINE' | 'MAINTENANCE' | 'ERROR';

export interface DeviceOperationStation {
  id: number;
  code: string;
  name: string;
}

export interface DeviceOperationLastEvent {
  id: number;
  type: DeviceEventType;
  severity: LogSeverity;
  message: string;
  origin: LogOrigin;
  source: DeviceEventSource;
  occurredAt: string;
}

export interface DeviceOperation {
  id: number;
  code: string;
  name: string;
  type: DeviceType;
  status: DeviceStatus;
  lastConnectionAt: string | null;
  station: DeviceOperationStation;
  lastEvent?: DeviceOperationLastEvent | null;
}

export interface DeviceOperationSummary {
  totalDevices: number;
  filteredDevices: number;
  byType: Record<DeviceType, number>;
  byStatus: Record<DeviceStatus, number>;
}

export interface DeviceOperationsResponse {
  evaluatedAt: string;
  summary: DeviceOperationSummary;
  devices: DeviceOperation[];
}
