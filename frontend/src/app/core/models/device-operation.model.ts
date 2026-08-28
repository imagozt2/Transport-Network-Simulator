import {
  DeviceEventSource,
  DeviceEventType,
  LogOrigin,
  LogSeverity
} from './operational-log.types';

export type DeviceType = 'TICKET_MACHINE' | 'ENTRY_VALIDATOR' | 'EXIT_VALIDATOR';
export type DeviceStatus = 'ONLINE' | 'OFFLINE' | 'MAINTENANCE' | 'ERROR';
export type DeviceConnectivityState = 'CONNECTED' | 'DISCONNECTED' | 'NOT_MONITORED';
export type DeviceMqttPresence = 'ONLINE' | 'OFFLINE';
export type DeviceOperationalState =
  | 'AVAILABLE'
  | 'BUSY'
  | 'DEGRADED'
  | 'OUT_OF_SERVICE'
  | 'MAINTENANCE';

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

export interface DeviceConnectivity {
  state: DeviceConnectivityState;
  mqttPresence: DeviceMqttPresence;
  operationalState: DeviceOperationalState;
  lastCommunicationAt: string | null;
  lastPresenceAt: string | null;
  lastStatusAt: string | null;
  serviceMode: string | null;
  softwareVersion: string | null;
  uptimeSeconds: number | null;
}

export interface DeviceOperation {
  id: number;
  code: string;
  name: string;
  type: DeviceType;
  status: DeviceStatus;
  lastConnectionAt: string | null;
  connectivity: DeviceConnectivity;
  station: DeviceOperationStation;
  lastEvent?: DeviceOperationLastEvent | null;
}

export interface DeviceOperationSummary {
  totalDevices: number;
  filteredDevices: number;
  byType: Record<DeviceType, number>;
  byStatus: Record<DeviceStatus, number>;
  byConnectivity: Record<DeviceConnectivityState, number>;
}

export interface DeviceOperationsResponse {
  evaluatedAt: string;
  summary: DeviceOperationSummary;
  devices: DeviceOperation[];
}
