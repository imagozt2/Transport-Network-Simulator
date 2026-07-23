export type DeviceType = 'TICKET_MACHINE' | 'ENTRY_VALIDATOR' | 'EXIT_VALIDATOR';
export type DeviceStatus = 'ONLINE' | 'OFFLINE' | 'MAINTENANCE' | 'ERROR';
export type DeviceEventType =
  | 'DEVICE_ONLINE'
  | 'DEVICE_OFFLINE'
  | 'DEVICE_STATUS_CHANGED'
  | 'DEVICE_ERROR'
  | 'DEVICE_MAINTENANCE_STARTED'
  | 'DEVICE_MAINTENANCE_FINISHED'
  | 'TICKET_PURCHASE_REQUESTED'
  | 'TICKET_PURCHASE_COMPLETED'
  | 'TICKET_PURCHASE_FAILED'
  | 'QR_TICKET_GENERATED'
  | 'QR_TICKET_SCANNED'
  | 'VALIDATION_REQUESTED'
  | 'VALIDATION_ACCEPTED'
  | 'VALIDATION_REJECTED'
  | 'VALIDATION_FAILED';
export type LogSeverity = 'DEBUG' | 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL';
export type LogOrigin = 'DEVICE_SIMULATION' | 'MQTT';

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
  lastEvent: DeviceOperationLastEvent | null;
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
